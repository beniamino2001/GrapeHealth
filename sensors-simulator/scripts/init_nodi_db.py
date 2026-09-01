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

Due contesti di esecuzione, non uno:
- da host, manuale: `cd sensors-simulator && python scripts/init_nodi_db.py`,
  con un .env locale letto da load_dotenv();
- dentro il container Tomcat, automatico: start-instances.sh lo invoca a ogni
  avvio del container, prima delle cinque istanze Tomcat. Lì .env non esiste
  come file (mai copiato nell'immagine, v. infra/tomcat/Dockerfile), ma le
  stesse variabili arrivano già nell'ambiente del processo via docker-compose
  (env_file più gli override PG_HOST/RABBITMQ_HOST/MQTT_HOST specifici della
  rete Docker): load_dotenv() non trova nulla lì e non fa danno, os.environ
  ha comunque i valori giusti in entrambi i contesti.

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
# Stessa CA locale che firma postgres/rabbitmq/tomcat (v. scripts/genera-
# certificati-tls.sh); risolta relativamente a questo file, non da variabile
# d'ambiente, per funzionare identica sia da host sia dentro il container
# Tomcat — stesso principio già usato per CONFIG_PATH qui sopra.
CA_CERT_PATH = Path(__file__).resolve().parent.parent.parent / "certs" / "ca.crt"

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
    INSERT INTO nodo_sensore (codice, parcella_id, tipo_nodo, attivo, data_installazione)
    VALUES (%s, %s, %s, TRUE, CURRENT_DATE)
    ON CONFLICT (codice) DO UPDATE SET
        parcella_id = EXCLUDED.parcella_id,
        tipo_nodo = EXCLUDED.tipo_nodo,
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
    load_dotenv(override=True)  # protegge dal caso in cui una variabile stantia già esportata nella
    # shell (es. da una sessione precedente) prevalga su un .env appena rigenerato — rilevante solo
    # nell'esecuzione da host: dentro il container Tomcat, .env non esiste come file (v. sopra)

    with open(CONFIG_PATH, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    # Se POSTGRES_USER/POSTGRES_PASSWORD non sono nell'ambiente (es. .env non generato con scripts/setup-credenziali.sh),
    # lo script si interrompe subito con un messaggio esplicativo invece di tentare l'accesso con la credenziale condivisa in chiaro.
    try:
        pg_user = os.environ["POSTGRES_USER"]
        pg_password = os.environ["POSTGRES_PASSWORD"]
    except KeyError as exc:
        print(
            f"Variabile d'ambiente {exc} mancante: rigenera le credenziali con "
            "./scripts/setup-credenziali.sh dalla root del repository, poi rilancia "
            "questo script da host oppure ricrea il container ('docker compose up -d "
            "--build') se stai leggendo questo messaggio nei suoi log.",
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
            # pg_hba.conf accetta solo connessioni "hostssl": senza sslmode
            # esplicito, il default 'prefer' di psycopg2 cifrerebbe comunque
            # (il server lo richiede) ma senza verificare il certificato
            # contro alcuna CA — 'verify-full' verifica sia la catena sia
            # che l'host a cui ci si connette corrisponda al certificato.
            sslmode="verify-full",
            sslrootcert=str(CA_CERT_PATH),
        )
    except psycopg2.OperationalError as exc:
        print(f"Impossibile connettersi a PostgreSQL: {exc}", file=sys.stderr)
        sys.exit(1)

    totale_parcelle = 0
    totale_nodi = 0
    codici_correnti = []
    with conn, conn.cursor() as cur:
        for parcella in config.get("parcelle") or []:
            try:
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
                    cur.execute(UPSERT_NODO_QUERY, (nodo["codice"], parcella_id, nodo["tipo"]))
                    totale_nodi += 1
                    codici_correnti.append(nodo["codice"])
            except KeyError as exc:
                # A differenza di 'parcelle' mancante o vuota (gestita sopra come
                # "niente da sincronizzare", coerente con la guardia già presente
                # più sotto per non disattivare l'anagrafica su una config vuota),
                # una parcella PRESENTE ma con un campo assente è quasi certamente
                # un errore di battitura nel YAML: qui si preferisce fermarsi con
                # un messaggio chiaro piuttosto che proseguire con un dato a metà.
                # 'with conn' più sopra esegue comunque il rollback della
                # transazione aperta quando sys.exit() la attraversa.
                print(
                    f"config/nodi.yaml: campo {exc} mancante per la parcella "
                    f"{parcella.get('nome', '<parcella senza nome>')}. Questo "
                    f"script non applica la stessa validazione semantica di "
                    f"simulator/main.py (valida_config()) — qui basta che il "
                    f"campo esista, il suo valore non viene verificato — ma un "
                    f"campo assente produrrebbe altrimenti un KeyError non "
                    f"diagnosticabile a metà della sincronizzazione.",
                    file=sys.stderr,
                )
                sys.exit(1)

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