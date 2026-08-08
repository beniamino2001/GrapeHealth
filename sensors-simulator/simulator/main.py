"""
Orchestratore del simulatore GrapeHealth.

Uso:
    cd sensors-simulator
    source .venv/bin/activate (se non è stato già configurato il venv, "python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt")
    python -m simulator.main
    python -m simulator.main --scenario stress_idrico --time-scale 288

--time-scale sovrascrive simulazione.time_scale nel config: con 288 un
giorno simulato passa in circa 5 minuti reali (86400 / 288 = 300s),
utile per attraversare rapidamente più giorni senza pioggia e osservare
la deriva del potenziale idrico.
"""

import argparse
import json
import logging
import os
import time
from pathlib import Path

import yaml
from dotenv import load_dotenv

from simulator.clock import SimulatedClock
from simulator.generator import StatoParcella, genera_letture_nodo, genera_temp_aria
from simulator.mqtt_client import GrapeHealthMqttClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("grapehealth.simulator")

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "nodi.yaml"


def carica_config() -> dict:
    with open(CONFIG_PATH, encoding="utf-8") as f:
        return yaml.safe_load(f)


def parse_args():
    parser = argparse.ArgumentParser(description="Simulatore nodi IoT GrapeHealth")
    parser.add_argument("--scenario", choices=["normale", "stress_idrico", "ondata_di_calore"],
                         help="Sovrascrive lo scenario definito in config/nodi.yaml")
    parser.add_argument("--time-scale", type=float,
                         help="Sovrascrive simulazione.time_scale definito in config/nodi.yaml")
    return parser.parse_args()


def main():
    load_dotenv()
    args = parse_args()
    config = carica_config()

    scenario = args.scenario or config["simulazione"]["scenario"]
    time_scale = args.time_scale or config["simulazione"]["time_scale"]
    intervallo_sim = config["simulazione"]["intervallo_pubblicazione_secondi"]
    prefix = config["mqtt"]["topic_prefix"]

    logger.info("Scenario attivo: %s | time_scale: %s | intervallo pubblicazione: %ss simulati",
                scenario, time_scale, intervallo_sim)

    clock = SimulatedClock(time_scale=time_scale)
    client = GrapeHealthMqttClient(client_id="sensori-simulati")
    client.connect()

    # una StatoParcella per parcella: mantiene la memoria di psi_stem/pioggia
    stati = {p["nome"]: StatoParcella(scenario) for p in config["parcelle"]}

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

            time.sleep(sleep_reale)

    except KeyboardInterrupt:
        logger.info("Interruzione richiesta, chiusura in corso...")
    finally:
        client.disconnect()


if __name__ == "__main__":
    main()