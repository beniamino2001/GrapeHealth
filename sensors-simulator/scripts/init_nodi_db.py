"""
Sincronizza la tabella nodo_sensore con quanto definito in config/nodi.yaml.

Pensato per essere rilanciato a ogni avvio: se cambi parcelle o nodi nel
YAML, esegui di nuovo questo script e il database si allinea (upsert su
`codice`, che deve quindi essere UNIQUE nello schema). L'allineamento è bidirezionale: 
i nodi rimossi dal YAML rispetto a un'esecuzione precedente vengono disattivati
(`attivo = FALSE`), non solo quelli presenti vengono upsertati, altrimenti
un nodo decommissionato sarebbe rimasto marcato "attivo" per sempre, raccontando
una topologia hardware non più reale. Le parcelle non vengono mai disattivate poiché
la tabella `parcella` non ha una colonna `attivo` nello schema attuale, 
coerentemente con l'assunzione che una parcella (a differenza di un singolo nodo) 
non venga mai rimossa senza un intervento diretto sullo schema.

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

UPSERT_PARCELLA_QUERY = """
    INSERT INTO parcella (nome, varieta, colore_bacca, lunghezza_germoglio_cm,
                           germoglio_aggiornato_il, latitudine, longitudine)
    VALUES (%s, %s, %s, %s, CURRENT_DATE, %s, %s)
    ON CONFLICT (nome) DO UPDATE SET
        varieta = EXCLUDED.varieta,
        colore_bacca = EXCLUDED.colore_bacca,
        lunghezza_germoglio_cm = EXCLUDED.lunghezza_germoglio_cm,
        -- Aggiorna la data solo se il valore è davvero cambiato rispetto a
        -- quello già presente: altrimenti un semplice rilancio dello script
        -- (es. per sincronizzare un nodo nuovo, senza alcun sopralluogo reale)
        -- farebbe apparire il dato fenologico più fresco di quanto sia.
        -- IS DISTINCT FROM gestisce correttamente il caso NULL (prima rilevazione).
        germoglio_aggiornato_il = CASE
            WHEN parcella.lunghezza_germoglio_cm IS DISTINCT FROM EXCLUDED.lunghezza_germoglio_cm
            THEN CURRENT_DATE
            ELSE parcella.germoglio_aggiornato_il
        END,
        latitudine = EXCLUDED.latitudine,
        longitudine = EXCLUDED.longitudine
    RETURNING id;
"""

UPSERT_NODO_QUERY = """
    INSERT INTO nodo_sensore (codice, parcella_id, tipo_nodo, latitudine, longitudine, attivo, data_installazione)
    VALUES (%s, %s, %s, %s, %s, TRUE, CURRENT_DATE)
    ON CONFLICT (codice) DO UPDATE SET
        parcella_id = EXCLUDED.parcella_id,
        tipo_nodo = EXCLUDED.tipo_nodo,
        latitudine = EXCLUDED.latitudine,
        longitudine = EXCLUDED.longitudine,
        attivo = TRUE;
"""

# Disattiva solo i nodi ATTUALMENTE attivi il cui codice non compare più fra
# quelli appena sincronizzati: un nodo già disattivato in precedenza non
# genera un no-op superfluo, e un nodo il cui codice torna nel YAML dopo
# essere sparito viene ri-attivato dall'upsert sopra, non da questa query.
DEACTIVATE_ORPHANED_NODI_QUERY = """
    UPDATE nodo_sensore
    SET attivo = FALSE
    WHERE attivo = TRUE
      AND codice <> ALL(%s);
"""


def main():
    load_dotenv()

    with open(CONFIG_PATH, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    # Se POSTGRES_USER/POSTGRES_PASSWORD non sono nell'ambiente (es. .env non generato con scripts/setup-credentials.sh),
    # lo script si interrompe subito con un messaggio esplicativo invece di tentare l'accesso con la credenziale condivisa in chiaro.
    try:
        pg_user = os.environ["POSTGRES_USER"]
        pg_password = os.environ["POSTGRES_PASSWORD"]
    except KeyError as exc:
        print(
            f"Variabile d'ambiente {exc} mancante: esegui ./scripts/setup-credentials.sh "
            "dalla root del repository e ricarica l'ambiente (.env) prima di rilanciare questo script.",
            file=sys.stderr,
        )
        sys.exit(1)

    try:
        conn = psycopg2.connect(
            host=os.environ.get("PG_HOST", "localhost"),
            port=os.environ.get("PG_PORT", 5432),
            dbname=os.environ.get("POSTGRES_DB", "grapehealth"),
            user=pg_user,
            password=pg_password,
        )
    except psycopg2.OperationalError as exc:
        print(f"Impossibile connettersi a PostgreSQL: {exc}", file=sys.stderr)
        sys.exit(1)

    totale_parcelle = 0
    totale_nodi = 0
    codici_correnti = []
    with conn, conn.cursor() as cur:
        for parcella in config["parcelle"]:
            cur.execute(UPSERT_PARCELLA_QUERY, (
                parcella["nome"],
                parcella["varieta"],
                parcella["colore_bacca"],
                parcella["stadio_fenologico_germogli_cm"],
                parcella["latitudine"],
                parcella["longitudine"],
            ))
            parcella_id = cur.fetchone()[0]
            totale_parcelle += 1

            for nodo in parcella["nodi"]:
                cur.execute(UPSERT_NODO_QUERY, (
                    nodo["codice"],
                    parcella_id,
                    nodo["tipo"],
                    parcella["latitudine"],
                    parcella["longitudine"],
                ))
                totale_nodi += 1
                codici_correnti.append(nodo["codice"])

        # Protezione contro uno YAML vuoto o mal formato (nessun nodo letto) 
        # il quale non deve disattivare l'intera anagrafica per un incidente di configurazione.
        # NOT IN/ <> ALL su una lista vuota sarebbe vero per ogni riga esistente.
        totale_disattivati = 0
        if codici_correnti:
            cur.execute(DEACTIVATE_ORPHANED_NODI_QUERY, (codici_correnti,))
            totale_disattivati = cur.rowcount
        else:
            print(
                "Attenzione: nessun nodo trovato in config/nodi.yaml, "
                "disattivazione dei nodi orfani saltata per sicurezza.",
                file=sys.stderr,
            )

    conn.close()
    print(f"Sincronizzate {totale_parcelle} parcelle e {totale_nodi} nodi su nodo_sensore "
          f"({totale_disattivati} nodi disattivati perché non più presenti nel YAML).")


if __name__ == "__main__":
    main()