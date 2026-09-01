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
import math
import os
import random
import re
import signal
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


# nome parcella e codice nodo finiscono, senza ulteriore escaping, in tre posti
# che trattano "." e "#" come caratteri di controllo, non come contenuto:
# il topic MQTT (f"{prefix}/{nome}/{codice}/{parametro}"), la routing key AMQP
# in cui RabbitMQ lo traduce (dove "." separa i segmenti e "#"/"*" sono
# wildcard), e ogni riga di log che li interpola in un messaggio (dove un "\n"
# incorporato falsificherebbe righe di log aggiuntive). Lo stesso pattern è
# stato aggiunto lato Java su MisurazioneMessage.nodo/parcella
# (@Pattern(regexp = "^[A-Za-z0-9_-]+$"), decisionengine) proprio perché un
# valore non conforme altera la routing key "allerta.<tipo>.<parcella>.<nodo>"
# pubblicata da AllertaPublisher — ma quel controllo scatta solo dopo che il
# dato è già stato pubblicato e ha già attraversato MQTT/AMQP; qui lo stesso
# controllo blocca l'avvio prima che un valore del genere venga mai pubblicato.
NOME_NODO_PARCELLA_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")

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

def valida_sezioni_config(config: dict) -> None:
    """Verifica che config abbia le due sezioni di primo livello che main()
    indicizza direttamente un istante dopo aver chiamato questa funzione
    (config["simulazione"][...], config["mqtt"][...]), prima ancora che
    valida_config() possa intervenire: le serve scenario/time_scale già
    risolti come argomenti, quindi non può girare più in alto di questa
    funzione. Senza questo controllo, una sezione mancante nel YAML produce
    un KeyError grezzo, bypassando ogni messaggio esplicito che il resto di
    questo file garantisce per ogni altro campo."""
    for sezione in ("simulazione", "mqtt"):
        if not isinstance(config.get(sezione), dict):
            raise ValueError(
                f"config/nodi.yaml privo della sezione '{sezione}' (trovato: "
                f"{config.get(sezione)!r})."
            )

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

    # isinstance() prima di math.isfinite(): quest'ultima solleva TypeError
    # (non un errore che questa funzione può intercettare e ripresentare come
    # ValueError leggibile) se time_scale_effettivo non è affatto un numero —
    # possibile da quando l'estrazione in main() è diventata un .get() che
    # restituisce None invece di sollevare KeyError su un campo assente.
    if (not isinstance(time_scale_effettivo, (int, float))
            or not math.isfinite(time_scale_effettivo) or time_scale_effettivo <= 0):
        raise ValueError(
            f"simulazione.time_scale non valido: {time_scale_effettivo!r}. Deve "
            f"essere un numero positivo e finito: zero causerebbe una divisione "
            f"per zero nel calcolo dell'intervallo reale fra letture, un valore "
            f"negativo farebbe scorrere il tempo simulato all'indietro, e "
            f"'nan'/'inf' (accettati da --time-scale in quanto float validi per "
            f"argparse, ma privi di senso fisico qui) mandano in crash "
            f"SimulatedClock.now() con un ValueError/OverflowError di basso "
            f"livello invece di questo messaggio esplicito."
        )

    intervallo_pubblicazione = config.get("simulazione", {}).get("intervallo_pubblicazione_secondi")
    if (not isinstance(intervallo_pubblicazione, (int, float))
            or not math.isfinite(intervallo_pubblicazione) or intervallo_pubblicazione <= 0):
        raise ValueError(
            f"simulazione.intervallo_pubblicazione_secondi non valido: "
            f"{intervallo_pubblicazione!r}. Deve essere un numero positivo e "
            f"finito, per lo stesso motivo di time_scale qui sopra: main() lo "
            f"divide per time_scale per calcolare l'attesa reale fra due tick "
            f"(sleep_reale = intervallo/time_scale) — mai validato finché "
            f"l'estrazione in main() sollevava comunque un KeyError su un "
            f"campo assente, prima che arrivasse fin qui."
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

    parcelle = config.get("parcelle")
    if not parcelle:
        raise ValueError(
            f"config/nodi.yaml privo di una sezione 'parcelle' non vuota "
            f"(trovato: {parcelle!r}). Senza questa sezione il simulatore "
            f"non avrebbe alcun nodo per cui generare letture: un dizionario "
            f"vuoto o una chiave mancante producono qui lo stesso errore "
            f"esplicito, invece di un KeyError grezzo più sotto nel ciclo "
            f"che itera su config['parcelle']."
        )

    for parcella in parcelle:
        nome_parcella = parcella.get("nome")
        if not nome_parcella or not NOME_NODO_PARCELLA_PATTERN.match(nome_parcella):
            raise ValueError(
                f"Nome parcella non valido: '{nome_parcella}'. Ammessi solo "
                f"lettere, cifre, '_' e '-': finisce nel topic MQTT, nella "
                f"routing key AMQP che ne deriva, e in ogni riga di log che lo "
                f"cita, dove '.', '#', '*' o un ritorno a capo hanno un "
                f"significato strutturale, non testuale."
            )
        colore = parcella.get("colore_bacca")
        if colore not in COLORI_BACCA_VALIDI:
            raise ValueError(
                f"colore_bacca non valido per {parcella.get('nome', '<parcella senza nome>')}: "
                f"'{colore}'. Valori ammessi: {', '.join(COLORI_BACCA_VALIDI)}."
            )
        nodi = parcella.get("nodi")
        if not nodi:
            raise ValueError(
                f"Parcella '{nome_parcella}' priva di una sezione 'nodi' non "
                f"vuota (trovato: {nodi!r}). Questo controllo esisteva prima "
                f"solo come .get('nodi', []) — accettava silenziosamente una "
                f"parcella senza nodi, ma il ciclo di pubblicazione più sotto "
                f"in questo stesso file usa parcella['nodi'] senza .get(): una "
                f"config che valida_config() avrebbe accettato avrebbe "
                f"comunque fatto crashare il programma al primo tick con un "
                f"KeyError, vanificando lo scopo stesso di validare prima di "
                f"qualunque effetto collaterale."
            )
        for nodo in nodi:
            codice_nodo = nodo.get("codice")
            if not codice_nodo or not NOME_NODO_PARCELLA_PATTERN.match(codice_nodo):
                raise ValueError(
                    f"Codice nodo non valido (parcella {nome_parcella}): "
                    f"'{codice_nodo}'. Ammessi solo lettere, cifre, '_' e '-', "
                    f"per lo stesso motivo del nome parcella qui sopra."
                )
            tipo = nodo.get("tipo")
            if tipo not in TIPI_NODO_VALIDI:
                raise ValueError(
                    f"tipo nodo non valido per {nodo.get('codice', '<nodo senza codice>')} "
                    f"(parcella {parcella.get('nome', '?')}): '{tipo}'. Valori ammessi: "
                    f"{', '.join(TIPI_NODO_VALIDI)}."
                )


def gestisci_sigterm(signum, frame):
    """Converte SIGTERM in KeyboardInterrupt: SIGINT (Ctrl+C, `kill -INT`) è
    già gestito correttamente nel blocco try/except di main(), ma un
    `docker stop`, un `systemctl stop` o un semplice `kill <pid>` senza
    `-INT` inviano SIGTERM per default. Senza questo handler, SIGTERM
    termina il processo immediatamente saltando il blocco finally che
    salva lo stato di sessione e pubblica lo stato "offline" via MQTT.
    Sollevare qui la stessa eccezione già gestita più sotto riusa il
    percorso di arresto pulito esistente invece di duplicarlo."""
    raise KeyboardInterrupt()


def valida_tasso_errori(tasso: float) -> None:
    """Verifica che --tasso-errori sia una probabilità valida (0.0-1.0):
    un valore fuori range produrrebbe un comportamento silenziosamente
    sbagliato in random.random() < tasso più sotto — per esempio un tasso
    di 50 corromperebbe ogni singola lettura senza alcun errore visibile,
    invece di essere rifiutato subito come un valore mal digitato."""
    if not 0.0 <= tasso <= 1.0:
        raise ValueError(
            f"--tasso-errori deve essere compreso fra 0.0 e 1.0, ricevuto {tasso}."
        )


def corrompi_payload(payload: dict) -> str:
    """Restituisce una stringa deliberatamente non-JSON al posto della
    serializzazione corretta di payload, conservando il nodo di origine
    nel testo per restare identificabile in una coda di dead-letter.
    Fallisce già alla conversione del messaggio lato Java, prima ancora
    della deserializzazione nel record applicativo — lo stesso scenario
    che finora andava riprodotto pubblicando a mano un messaggio non-JSON,
    qui automatizzato con traffico reale di questa fase. Usata solo
    quando --tasso-errori (v. parse_args()) è maggiore di zero."""
    return f'{{"nodo": "{payload["nodo"]}", "guasto_simulato": true,'


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
    parser.add_argument("--tasso-errori", type=float, default=0.0,
                         help="Probabilità (0.0-1.0) che ciascuna lettura pubblicata sia "
                              "deliberatamente corrotta (JSON non valido), per esercitare le "
                              "code di dead-letter dei moduli a valle con traffico reale invece "
                              "che con messaggi costruiti a mano. Default 0.0 (nessuna corruzione).")
    return parser.parse_args()


def main():
    signal.signal(signal.SIGTERM, gestisci_sigterm)
    load_dotenv(override=True)  # protegge dal caso in cui una variabile stantia già esportata nella
    # shell (es. da una sessione precedente) prevalga su un .env appena rigenerato — rilevante solo
    # nell'esecuzione da host: dentro il container Tomcat, .env non esiste come file (v. sopra)
    args = parse_args()
    config = carica_config()

    # Verificato prima di qualunque estrazione: le quattro righe subito sotto
    # leggono config["simulazione"][...] e config["mqtt"][...] con
    # indicizzazione diretta, un istante prima che valida_config() abbia
    # anche solo la possibilità di intervenire.
    valida_sezioni_config(config)

    scenario = valore_effettivo(args.scenario, config["simulazione"].get("scenario"))
    time_scale = valore_effettivo(args.time_scale, config["simulazione"].get("time_scale"))
    intervallo_sim = config["simulazione"].get("intervallo_pubblicazione_secondi")
    prefix = config["mqtt"].get("topic_prefix")

    # Fallisce subito e rumorosamente su un valore non riconosciuto, invece
    # di lasciare che un typo (o uno zero) produca dati silenziosamente
    # sbagliati, o un crash oscuro più avanti, per l'intera durata della run.
    valida_config(config, scenario, time_scale)
    valida_tasso_errori(args.tasso_errori)

    logger.info("Scenario attivo: %s | time_scale: %s | intervallo pubblicazione: %ss simulati",
                scenario, time_scale, intervallo_sim)
    if args.tasso_errori > 0:
        logger.warning(
            "--tasso-errori=%.3f attivo: circa una lettura su %d sarà pubblicata "
            "deliberatamente corrotta, per verificare la gestione degli errori a valle. "
            "Non usare questa opzione per una run che deve alimentare dati puliti.",
            args.tasso_errori, round(1 / args.tasso_errori),
        )

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
    sleep_reale = max(PAVIMENTO_SLEEP_REALE_SECONDI, intervallo_sim / time_scale)

    # Dichiarato vuoto qui, fuori dal try: così il finally può sempre
    # accedervi anche se un'interruzione arriva durante client.connect(),
    # prima ancora che stati venga popolato più sotto — in quel caso non
    # esiste ancora alcuno stato fisico reale da preservare, un dizionario
    # vuoto passato a salva_stato_sessione() è innocuo (v. test_state.py).
    stati = {}

    try:
        # Dentro il blocco protetto, non prima: con il backoff di connect()
        # (fino a MAX_TENTATIVI_CONNESSIONE_INIZIALE tentativi, anche minuti
        # se il broker tarda ad avviarsi) una SIGINT/SIGTERM che arrivasse
        # durante l'attesa, se connect() restasse fuori da questo blocco,
        # salterebbe comunque il salvataggio dello stato e la disconnessione
        # pulita più sotto.
        client.connect()

        # una StatoParcella per parcella: mantiene la memoria di psi_stem/pioggia,
        # ripristinata dalla sessione precedente quando disponibile
        stati_salvati = (stato_precedente or {}).get("parcelle", {})
        for p in config["parcelle"]:
            sp = StatoParcella(scenario)
            if p["nome"] in stati_salvati:
                sp.ripristina_stato(stati_salvati[p["nome"]])
            stati[p["nome"]] = sp

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
                        if args.tasso_errori > 0 and random.random() < args.tasso_errori:
                            corpo = corrompi_payload(payload)
                            logger.warning(
                                "Pubblicazione deliberatamente corrotta su %s "
                                "(--tasso-errori attivo): verifica della gestione degli "
                                "errori a valle, non un malfunzionamento.",
                                topic,
                            )
                        else:
                            corpo = json.dumps(payload)
                        client.publish(topic, corpo, qos=1)

            salva_stato_sessione(now, {nome: s.esporta_stato() for nome, s in stati.items()})
            time.sleep(sleep_reale)

    except KeyboardInterrupt:
        logger.info("Interruzione richiesta, chiusura in corso...")
    finally:
        salva_stato_sessione(clock.now(), {nome: s.esporta_stato() for nome, s in stati.items()})
        client.disconnect()


if __name__ == "__main__":
    main()