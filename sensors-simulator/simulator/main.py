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
from simulator.generator import StatoParcella, genera_letture_nodo, genera_temp_aria
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
TIPI_NODO_VALIDI = ("meteo", "idrico", "bacca")

TOPIC_PREFIX_ATTESO = "grapehealth"


def carica_config() -> dict:
    with open(CONFIG_PATH, encoding="utf-8") as f:
        return yaml.safe_load(f)


def valida_config(config: dict, scenario_effettivo: str) -> None:
    """Verifica all'avvio i valori letti da config/nodi.yaml che il resto del
    codice si limita a confrontare per uguaglianza. Un typo
    plausibile (es.: 'Nero' invece di 'nero') non produce alcun
    errore a runtime: viene silenziosamente interpretato come il valore
    "else" del confronto (colore_bacca sconosciuto -> trattato come bianco,
    offset di temp_bacca inferiore di 4°C; scenario sconosciuto -> nessuna
    delle condizioni scenario-specifiche scatta, comportamento di default
    equivalente a 'normale'), con differenze fino a 5-8°C sui valori generati senza
    alcun segnale visibile.

    Include anche mqtt.topic_prefix, è un contratto con un valore
    hardcoded lato Java su DUE moduli (INPUT_ROUTING_KEY in
    backend/.../decisionengine/config/RabbitConfig.java e
    MISURAZIONI_ROUTING_KEY nell'equivalente in persistence), entrambi con
    binding "grapehealth.#". Cambiarlo qui da solo instraderebbe silenziosamente OGNI messaggio
    pubblicato dal simulatore verso il nulla: nessun binding RabbitMQ
    corrisponderebbe più, senza alcun errore, alcun log, alcuna coda di
    dead-letter (che si applica solo ai messaggi già arrivati in coda e poi
    rifiutati, non a quelli mai instradati).

    Solleva ValueError con un messaggio che identifica esattamente il campo,
    il valore trovato e i valori ammessi, invece di un generico "config non
    valida" che costringerebbe a rileggere l'intero file per capire cosa
    correggere.
    """
    if scenario_effettivo not in SCENARI_VALIDI:
        raise ValueError(
            f"Scenario non valido: '{scenario_effettivo}' (letto da "
            f"config/nodi.yaml, simulazione.scenario, dato che --scenario "
            f"non è stato passato da riga di comando). Valori ammessi: "
            f"{', '.join(SCENARI_VALIDI)}."
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

    scenario = args.scenario or config["simulazione"]["scenario"]
    time_scale = args.time_scale or config["simulazione"]["time_scale"]
    intervallo_sim = config["simulazione"]["intervallo_pubblicazione_secondi"]
    prefix = config["mqtt"]["topic_prefix"]

    # Fallisce subito e rumorosamente su un valore non riconosciuto, invece
    # di lasciare che un typo produca dati silenziosamente sbagliati per
    # l'intera durata della run.
    valida_config(config, scenario)

    logger.info("Scenario attivo: %s | time_scale: %s | intervallo pubblicazione: %ss simulati",
                scenario, time_scale, intervallo_sim)

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

    sleep_reale = max(0.1, intervallo_sim / time_scale)

    try:
        while True:
            now = clock.now()

            for parcella in config["parcelle"]:
                stato = stati[parcella["nome"]]
                stato.aggiorna_se_nuovo_giorno(now)
                temp_aria_riferimento = genera_temp_aria(now, stato.scenario)

                for nodo in parcella["nodi"]:
                    letture = genera_letture_nodo(
                        tipo_nodo=nodo["tipo"],
                        dt=now,
                        stato=stato,
                        colore_bacca=parcella["colore_bacca"],
                        temp_aria_riferimento=temp_aria_riferimento,
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