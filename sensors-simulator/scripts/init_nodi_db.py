"""
Sincronizza la tabella nodo_sensore con quanto definito in config/nodi.yaml.

Pensato per essere rilanciato a ogni avvio: se cambi parcelle o nodi nel
YAML, esegui di nuovo questo script e il database si allinea (upsert su
`codice`, che deve quindi essere UNIQUE nello schema).

Uso:
    cd sensors-simulator
    source .venv/bin/activate (se non è stato già configurato il venv, "python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt")
    python scripts/init_nodi_db.py
"""

import os
import sys
from pathlib import Path

import psycopg2
import yaml
from dotenv import load_dotenv

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config" / "nodi.yaml"

UPSERT_QUERY = """
    INSERT INTO nodo_sensore (codice, parcella, tipo_nodo, latitudine, longitudine, attivo, data_installazione)
    VALUES (%s, %s, %s, %s, %s, TRUE, CURRENT_DATE)
    ON CONFLICT (codice) DO UPDATE SET
        parcella = EXCLUDED.parcella,
        tipo_nodo = EXCLUDED.tipo_nodo,
        latitudine = EXCLUDED.latitudine,
        longitudine = EXCLUDED.longitudine,
        attivo = TRUE;
"""


def main():
    load_dotenv()

    with open(CONFIG_PATH, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    try:
        conn = psycopg2.connect(
            host=os.environ.get("PG_HOST", "localhost"),
            port=os.environ.get("PG_PORT", 5432),
            dbname=os.environ.get("POSTGRES_DB", "grapehealth"),
            user=os.environ.get("POSTGRES_USER", "grapehealth"),
            password=os.environ.get("POSTGRES_PASSWORD", "grapehealth"),
        )
    except psycopg2.OperationalError as exc:
        print(f"Impossibile connettersi a PostgreSQL: {exc}", file=sys.stderr)
        sys.exit(1)

    totale = 0
    with conn, conn.cursor() as cur:
        for parcella in config["parcelle"]:
            for nodo in parcella["nodi"]:
                cur.execute(UPSERT_QUERY, (
                    nodo["codice"],
                    parcella["nome"],
                    nodo["tipo"],
                    parcella["latitudine"],
                    parcella["longitudine"],
                ))
                totale += 1

    conn.close()
    print(f"Sincronizzati {totale} nodi su nodo_sensore.")


if __name__ == "__main__":
    main()
