"""
Orchestratore del simulatore GrapeHealth.

Uso:
    cd sensors-simulator
    source .venv/bin/activate (se non è stato già configurato il venv, "python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt")
    python -m simulator.main
    python -m simulator.main --scenario stress_idrico --time-scale 288
    python -m simulator.main --scenario ondata_di_calore --reset-sessione

--time-scale sovrascrive simulazione.time_scale nel config: con 288 un
giorno simulato passa in circa 5 minuti reali (86400 / 288 = 300s),
utile per attraversare rapidamente più giorni senza pioggia e osservare
la deriva del potenziale idrico.

Sessioni successive riprendono automaticamente dall'ultimo istante
simulato raggiunto (e dalle condizioni di ogni parcella) della sessione
precedente, anche cambiando --scenario da un avvio all'altro: è così che
si costruisce una storia simulata continua su più esecuzioni, invece di
ripartire ogni volta da "adesso" con Ψstem resettato. Usa --reset-sessione
per scartare esplicitamente lo stato precedente e ripartire da zero.
"""

import argparse
import json
import logging
import os
import time
from datetime import datetime
from pathlib import Path

import yaml
from dotenv import load_dotenv

from simulator.clock import SimulatedClock
from simulator.generator import StatoParcella, genera_letture_nodo, genera_temp_aria, genera_velocita_vento
from simulator.mqtt_client import GrapeHealthMqttClient
from simulator.state import carica_stato_sessione, elimina_stato_sessione, salva_stato_sessione

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("grapehealth.simulator")

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "nodi.yaml"

# Elencati solo gli scenari validi: usata sia da argparse (choices,
# così un typo su --scenario da riga di comando viene rifiutato subito da
# argparse stesso) sia da valida_config() qui sotto (così un typo nel valore
# di default scritto in config/nodi.yaml, mai passato da riga di comando,
# non sfugge silenziosamente alla stessa protezione).
SCENARI_VALIDI = ("normale", "stress_idrico", "ondata_di_calore")
COLORI_BACCA_VALIDI = ("nero", "bianco")
TIPI_NODO_VALIDI = ("meteo", "idrico", "bacca", "suolo")

TOPIC_PREFIX_ATTESO = "grapehealth"


def avviso_time_scale(time_scale: float) -> str | None:
    """Messaggio da loggare quando time_scale diverge da 1, None altrimenti.
    Funzione pura (nessun logging al suo interno) per poter verificare il
    contenuto del messaggio senza dover eseguire main()."""
    if time_scale == 1:
        return None
    return (
        f"time_scale={time_scale}: i timestamp pubblicati si allontaneranno "
        f"rapidamente dall'ora reale di questa macchina. Qualunque sistema a "
        f"valle che confronta questi dati con l'orologio reale del proprio "
        f"ambiente invece che con l'ultimo timestamp osservato nel flusso "
        f"otterrà risultati vuoti o incoerenti: non è un malfunzionamento, è "
        f"la conseguenza diretta di time_scale != 1."
    )


PAVIMENTO_SLEEP_REALE_SECONDI = 0.1


def avviso_intervallo_sovrascritto(intervallo_pubblicazione_secondi: float, time_scale: float) -> str | None:
    """Messaggio da loggare quando il pavimento minimo su sleep_reale (v.
    ciclo principale di main()) sta sovrascrivendo silenziosamente
    l'intervallo configurato, None altrimenti. A time_scale abbastanza alti,
    intervallo_pubblicazione_secondi/time_scale scende sotto quel pavimento:
    il ciclo continua comunque a dormire il minimo consentito, ma l'intervallo
    simulato realmente attraversato fra due tick diventa
    PAVIMENTO_SLEEP_REALE_SECONDI * time_scale, non più quello dichiarato in
    config — una condizione che resta vera per una manciata di ore simulate
    genera così molti più tick, e molte più occasioni di riallerta per una
    regola priva di isteresi, di quanti l'intervallo configurato lascerebbe
    pensare."""
    sleep_reale_nominale = intervallo_pubblicazione_secondi / time_scale
    if sleep_reale_nominale >= PAVIMENTO_SLEEP_REALE_SECONDI:
        return None
    intervallo_effettivo = PAVIMENTO_SLEEP_REALE_SECONDI * time_scale
    return (
        f"intervallo_pubblicazione_secondi={intervallo_pubblicazione_secondi} "
        f"con time_scale={time_scale} richiederebbe di dormire "
        f"{sleep_reale_nominale:.4f}s fra un tick e l'altro, sotto il "
        f"pavimento di {PAVIMENTO_SLEEP_REALE_SECONDI}s: l'intervallo "
        f"realmente attraversato in secondi simulati sarà "
        f"{intervallo_effettivo:.0f}s, non {intervallo_pubblicazione_secondi}. "
        f"Non è un malfunzionamento, ma un valore di config che a questo "
        f"time_scale non descrive più la cadenza reale delle pubblicazioni."
    )


def valore_effettivo(da_cli, da_config):
    """args.scenario/args.time_scale sono None se non passati da riga di
    comando, mai 0 o stringa vuota (argparse type=float li accetta come
    valori validi, choices= esclude una stringa vuota per --scenario). `or`
    fallirebbe silenziosamente su --time-scale 0 (0 è falsy in Python),
    ignorando un valore esplicitamente passato a favore del default di
    config — `is not None` è la scelta corretta qui.
    """
    return da_cli if da_cli is not None else da_config


def carica_config() -> dict:
    with open(CONFIG_PATH, encoding="utf-8") as f:
        return yaml.safe_load(f)


def valida_config(config: dict, scenario_effettivo: str, time_scale_effettivo: float) -> None:
    """Verifica all'avvio i valori che il resto del codice si limita a
    confrontare per uguaglianza. Un typo (es. 'Nero' invece di 'nero') non
    produce errori a runtime: cade silenziosamente nel ramo "else" del
    confronto, con differenze fino a 5-8°C sui valori generati senza alcun
    segnale visibile.

    mqtt.topic_prefix è un caso a parte: è hardcoded lato Java in due moduli
    (RabbitConfig del decision engine e di persistence, routing key
    "grapehealth.#"). Cambiarlo qui da solo instraderebbe silenziosamente
    ogni messaggio verso il nulla — nessun binding corrisponderebbe più,
    senza errori né dead-letter (che si applica solo a messaggi già in
    coda, non a quelli mai instradati).

    time_scale non passa per un confronto per uguaglianza come gli altri
    campi, ma per una divisione (sleep_reale = intervallo/time_scale) e per
    una moltiplicazione nell'orologio simulato: zero manderebbe in crash la
    divisione, un valore negativo farebbe scorrere il tempo simulato
    all'indietro, violando la garanzia di monotonicità di SimulatedClock.

    Solleva ValueError con campo, valore trovato e valori ammessi, non un
    generico "config non valida".
    """
    if scenario_effettivo not in SCENARI_VALIDI:
        raise ValueError(
            f"Scenario non valido: '{scenario_effettivo}' (letto da "
            f"config/nodi.yaml, simulazione.scenario, dato che --scenario "
            f"non è stato passato da riga di comando). Valori ammessi: "
            f"{', '.join(SCENARI_VALIDI)}."
        )

    if time_scale_effettivo <= 0:
        raise ValueError(
            f"simulazione.time_scale non valido: {time_scale_effettivo}. Deve "
            f"essere un numero positivo: zero causerebbe una divisione per "
            f"zero nel calcolo dell'intervallo reale fra letture, un valore "
            f"negativo farebbe scorrere il tempo simulato all'indietro."
        )

    topic_prefix = config.get("mqtt", {}).get("topic_prefix")
    if topic_prefix != TOPIC_PREFIX_ATTESO:
        raise ValueError(
            f"mqtt.topic_prefix non valido: '{topic_prefix}'. Il solo valore "
            f"corretto è '{TOPIC_PREFIX_ATTESO}': è hardcoded lato Java come "
            f"routing key '{TOPIC_PREFIX_ATTESO}.#' sia in "
            f"backend/.../decisionengine/config/RabbitConfig.java "
            f"(INPUT_ROUTING_KEY) sia nell'equivalente in persistence "
            f"(MISURAZIONI_ROUTING_KEY). Cambiare questo valore qui da solo "
            f"instraderebbe silenziosamente ogni misurazione pubblicata "
            f"verso il nulla, senza alcun errore visibile: se serve davvero "
            f"cambiarlo, va aggiornato in tutti e tre i punti insieme."
        )

    for parcella in config["parcelle"]:
        colore = parcella.get("colore_bacca")
        if colore not in COLORI_BACCA_VALIDI:
            raise ValueError(
                f"colore_bacca non valido per {parcella.get('nome', '<parcella senza nome>')}: "
                f"'{colore}'. Valori ammessi: {', '.join(COLORI_BACCA_VALIDI)}."
            )
        for nodo in parcella.get("nodi", []):
            tipo = nodo.get("tipo")
            if tipo not in TIPI_NODO_VALIDI:
                raise ValueError(
                    f"tipo nodo non valido per {nodo.get('codice', '<nodo senza codice>')} "
                    f"(parcella {parcella.get('nome', '?')}): '{tipo}'. Valori ammessi: "
                    f"{', '.join(TIPI_NODO_VALIDI)}."
                )


def parse_args():
    parser = argparse.ArgumentParser(description="Simulatore nodi IoT GrapeHealth")
    parser.add_argument("--scenario", choices=list(SCENARI_VALIDI),
                         help="Sovrascrive lo scenario definito in config/nodi.yaml")
    parser.add_argument("--time-scale", type=float,
                         help="Sovrascrive simulazione.time_scale definito in config/nodi.yaml")
    parser.add_argument("--reset-sessione", action="store_true",
                         help="Ignora lo stato di sessione salvato da un'esecuzione precedente "
                              "(linea temporale simulata e condizioni delle parcelle) e riparte "
                              "da 'adesso' con condizioni di default.")
    return parser.parse_args()


def main():
    load_dotenv()
    args = parse_args()
    config = carica_config()

    scenario = valore_effettivo(args.scenario, config["simulazione"]["scenario"])
    time_scale = valore_effettivo(args.time_scale, config["simulazione"]["time_scale"])
    intervallo_sim = config["simulazione"]["intervallo_pubblicazione_secondi"]
    prefix = config["mqtt"]["topic_prefix"]

    # Fallisce subito e rumorosamente su un valore non riconosciuto, invece
    # di lasciare che un typo (o uno zero) produca dati silenziosamente
    # sbagliati, o un crash oscuro più avanti, per l'intera durata della run.
    valida_config(config, scenario, time_scale)

    logger.info("Scenario attivo: %s | time_scale: %s | intervallo pubblicazione: %ss simulati",
                scenario, time_scale, intervallo_sim)

    messaggio_time_scale = avviso_time_scale(time_scale)
    if messaggio_time_scale:
        logger.warning(messaggio_time_scale)

    messaggio_intervallo = avviso_intervallo_sovrascritto(intervallo_sim, time_scale)
    if messaggio_intervallo:
        logger.warning(messaggio_intervallo)

    if args.reset_sessione:
        elimina_stato_sessione()
        stato_precedente = None
        logger.info("--reset-sessione: stato di sessione precedente scartato, riparto da 'adesso'.")
    else:
        stato_precedente = carica_stato_sessione()

    if stato_precedente:
        punto_di_partenza = datetime.fromisoformat(stato_precedente["ultimo_timestamp_simulato"])
        logger.info(
            "Ripresa la linea temporale simulata dalla sessione precedente: %s (scenario di questa sessione: %s)",
            punto_di_partenza.isoformat(), scenario,
        )
        logger.warning(
            "Se hai anche azzerato il database dall'ultima sessione (es. 'docker compose down -v'), "
            "questa ripresa pubblicherà misurazioni con timestamp lontano da 'adesso' dentro un database "
            "vuoto: rilancia con --reset-sessione per ripartire in sincronia con un database appena "
            "azzerato."
        )
    else:
        punto_di_partenza = None
        logger.info("Nessuna sessione precedente da riprendere, si parte da 'adesso'.")

    clock = SimulatedClock(time_scale=time_scale, start=punto_di_partenza)
    client = GrapeHealthMqttClient(client_id="sensori-simulati")
    client.connect()

    # una StatoParcella per parcella: mantiene la memoria di psi_stem/pioggia,
    # ripristinata dalla sessione precedente quando disponibile
    stati = {}
    stati_salvati = (stato_precedente or {}).get("parcelle", {})
    for p in config["parcelle"]:
        sp = StatoParcella(scenario)
        if p["nome"] in stati_salvati:
            sp.ripristina_stato(stati_salvati[p["nome"]])
        stati[p["nome"]] = sp

    sleep_reale = max(PAVIMENTO_SLEEP_REALE_SECONDI, intervallo_sim / time_scale)

    try:
        while True:
            now = clock.now()

            for parcella in config["parcelle"]:
                stato = stati[parcella["nome"]]
                stato.aggiorna_se_nuovo_giorno(now)
                temp_aria_riferimento = genera_temp_aria(now, stato.scenario)
                # calcolata una sola volta per parcella per tick e condivisa fra nodo meteo
                # e nodo bacca, sullo stesso principio già applicato a temp_aria_riferimento:
                # il raffreddamento da vento in genera_temp_bacca() deve usare lo stesso vento
                # realmente pubblicato in questo istante, non un secondo campione indipendente.
                velocita_vento_riferimento = genera_velocita_vento(now, stato.scenario)

                for nodo in parcella["nodi"]:
                    letture = genera_letture_nodo(
                        tipo_nodo=nodo["tipo"],
                        dt=now,
                        stato=stato,
                        colore_bacca=parcella["colore_bacca"],
                        temp_aria_riferimento=temp_aria_riferimento,
                        velocita_vento_riferimento=velocita_vento_riferimento,
                    )
                    for parametro, (valore, unita) in letture.items():
                        payload = {
                            "nodo": nodo["codice"],
                            "parcella": parcella["nome"],
                            "parametro": parametro,
                            "valore": valore,
                            "unita_misura": unita,
                            "timestamp_rilevazione": now.isoformat() + "Z",
                        }
                        topic = f"{prefix}/{parcella['nome']}/{nodo['codice']}/{parametro}"
                        client.publish(topic, json.dumps(payload), qos=1)

            salva_stato_sessione(now, {nome: s.esporta_stato() for nome, s in stati.items()})
            time.sleep(sleep_reale)

    except KeyboardInterrupt:
        logger.info("Interruzione richiesta, chiusura in corso...")
    finally:
        salva_stato_sessione(clock.now(), {nome: s.esporta_stato() for nome, s in stati.items()})
        client.disconnect()


if __name__ == "__main__":
    main()