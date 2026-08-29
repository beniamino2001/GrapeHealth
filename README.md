# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `PyYAML`, `python-dotenv`, `psycopg2-binary`, `pytest`
- OpenSSL e una JDK con `keytool`

## Avvio dell'intero stack applicativo da CLI in locale

```bash
chmod +x scripts/*   # solo la prima volta
sh scripts/setup-credentials.sh          # genera credenziali locali casuali e chiede due percorsi utili al Tomcat
sh scripts/genera-certificati-tls.sh    # genera una CA locale e i certificati TLS per i servizi infrastrutturali
docker compose up -d --build
```

N.B.: l'ordine di esecuzione conta poichè `genera-certificati-tls.sh` legge la password del keystore Java da `.env`, generata dal primo script — invertirli produce un errore esplicito.

`setup-credentials.sh` genera password casuali forti e chiede due percorsi assoluti su 
dove vivono le cinque istanze Tomcat locali e su dove scrivere i log applicativi delle app
Spring Boot (es. Downloads), verificandone l'esistenza prima di procedere e poi
scrive tutto in quattro file distinti:

- `secrets/postgres_user.txt` / `postgres_password.txt`, letti dal container PostgreSQL tramite
  Docker secrets (`POSTGRES_USER_FILE`/`POSTGRES_PASSWORD_FILE`);
- `infra/rabbitmq/definitions.json`, letto dal container RabbitMQ al boot
  (`management.load_definitions`): contiene l'utente amministrativo con la password **già
  hashata** (`rabbitmqctl hash_password`), non in chiaro;
- `.env`, letto sia da Docker Compose sia dalle app Spring Boot per autenticarsi come client
  AMQP/JDBC — qui la password resta necessariamente in chiaro, perché un client deve sempre
  presentare la credenziale reale per autenticarsi, non il suo hash. Contiene anche
  `TLS_KEYSTORE_PASSWORD`, la password dei keystore Java generati dal secondo script.

Lo script è pensato per essere eseguito una sola volta: se viene rilanciato e trova già uno di
questi quattro artefatti, non tocca nulla e stampa le istruzioni per una rigenerazione completa
e coerente, nell'ordine corretto — prima `docker compose down -v` (mentre `.env` esiste ancora,
perché il servizio `tomcat` lo richiede anche solo per potersi fermare), poi la cancellazione
dei file, poi la rigenerazione. Rigenerare le credenziali cambia anche `TLS_KEYSTORE_PASSWORD`:
cancella anche `certs/` e rilancia `genera-certificati-tls.sh` subito dopo, altrimenti i
certificati vecchi userebbero una password ormai sostituita. Se preferisci impostare le
credenziali manualmente, copia `.env.example` in `.env` e `secrets/*.txt.example` in
`secrets/*.txt`, sostituendo i placeholder con valori a tua scelta.

`docker compose up -d` avvia l'intero stack: PostgreSQL, RabbitMQ e un container `tomcat`
con Apache Tomcat 11/Eclipse Temurin JDK 21 usato come CATALINA_HOME per le cinque istanze applicative 
(`decisionengine`, `attuatori`, `persistence`, `api`, `dashboard`).

## Il container `tomcat`

Le cartelle reali delle cinque istanze Tomcat (`CATALINA_BASE`) e dei log applicativi delle app
Spring Boot **vivono al di fuori di questo repository**: sono percorsi appartenenti all'ambiente locale
indicati dalle variabili `TOMCAT_INSTANCES_DIR` e `SPRING_BOOT_LOGS_DIR` in `.env` e montati nel
container come bind mount.

- `infra/tomcat/Dockerfile` — costruisce l'immagine a partire dall'immagine con il tag `tomcat:11-jdk21-temurin-jammy`, con un Python minimale aggiunto solo per eseguire `sensors-simulator/scripts/init_nodi_db.py` prima di avviare le cinque istanze.
- `infra/tomcat/start-instances.sh` — l'entrypoint: avvia le cinque istanze in parallelo dalla
  `CATALINA_HOME` condivisa, ciascuna con la propria `CATALINA_BASE`. Alla prima esecuzione dopo
  un `docker compose down -v` (mai dopo un semplice `stop`) pulisce tutti i log delle istanze
  Tomcat e delle app Spring Boot prima di far ripartire lo stack, e sincronizza `nodo_sensore`
  con `sensors-simulator/config/nodi.yaml` eseguendo `init_nodi_db.py`, step necessario perché
  `persistence`/`api` caricano la mappa dei nodi noti una sola volta, al proprio avvio, e non la
  ricaricano da sole. Prima di avviare ciascuna istanza, rigenera anche `server.xml` da
  `server.xml.template` presente nella cartella `conf` di ciascuna di essa, sostituendo il segnaposto
  `__TLS_KEYSTORE_PASSWORD__` con la password corrente del keystore.
- `infra/tomcat/istanze-escluse.conf` — un'istanza per riga per escluderla dall'avvio, utile per
  isolare il troubleshooting su uno o più moduli senza fermare gli altri; di default è vuoto
  (tutte e cinque partono).

Porte esposte dal container `tomcat` — HTTPS, non più HTTP:

| Istanza | Porta |
|---|---|
| `decisionengine` | 8081 |
| `attuatori` | 8082 |
| `persistence` | 8083 |
| `api` | 8084 |
| `dashboard` | 8085 |

## Comunicazione cifrata (TLS)

Ogni servizio dell'infrastruttura parla TLS, connessioni in chiaro respinte esplicitamente
dove il protocollo lo consente — come in un ambiente di produzione, anche se tutto gira in
locale. `scripts/genera-certificati-tls.sh` genera una CA locale autofirmata (valida dieci
anni, pensata solo per questo ambiente di sviluppo) e un certificato per ciascun servizio:

- **PostgreSQL** — `hostssl` in `infra/postgres/pg_hba.conf`: qualunque connessione di rete
  senza TLS viene respinta esplicitamente, non solo resa opzionale. Il driver JDBC negozia SSL
  automaticamente, nessuna configurazione aggiuntiva lato client.
- **RabbitMQ** — AMQP su 5671 e MQTT su 8883, entrambi i listener in chiaro (5672/1883)
  disattivati in `infra/rabbitmq/rabbitmq.conf`. I tre moduli Java si autenticano con il
  truststore generato dallo stesso script (`SPRING_RABBITMQ_SSL_*` in `docker-compose.yml`).
  La Management UI è raggiungibile anche su TLS (15671), stesso certificato — qui la porta in
  chiaro (15672) resta attiva: è una console di amministrazione, non un canale dati.
- **Tomcat** — connettore HTTPS su ciascuna delle cinque porte (8081-8085), certificato e
  password del keystore condivisi dalle cinque istanze (un solo hostname, `tomcat`).

## Servizi esposti

| Servizio | Porta | Descrizione |
|---|---|---|
| RabbitMQ MQTT (TLS) | 8883 | Endpoint per la pubblicazione dei sensori IoT |
| RabbitMQ AMQP (TLS) | 5671 | Endpoint per i consumer applicativi |
| RabbitMQ Management UI | 15672 / 15671 (TLS) | Console web per management (credenziali generate da `setup-credentials.sh`); entrambe le porte restano attive, a differenza di AMQP/MQTT |
| PostgreSQL (TLS obbligatorio) | 5432 | Persistenza delle misurazioni, allerte e trattamenti |

Lo schema sul database viene creato automaticamente al primo avvio da `infra/postgres/init/01_schema.sql`.

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── scripts/
│   ├── setup-credentials.sh        # generazione credenziali locali + password keystore TLS
│   └── genera-certificati-tls.sh   # CA locale e certificati per postgres/rabbitmq/tomcat
├── secrets/
│   ├── postgres_user.txt.example
│   └── postgres_password.txt.example
├── infra/
│   ├── rabbitmq/       # Dockerfile (copia i certificati), configurazione broker MQTT, definitions.json
│   ├── postgres/       # Dockerfile (copia i certificati), pg_hba.conf, schema database
│   └── tomcat/         # Dockerfile, entrypoint e file di esclusione del container tomcat
├── backend/            # decision engine: consumer AMQP, regole fitosanitarie/climatiche
├── attuatori/          # simulatore di attuazione: consumer AMQP, log strutturati
├── persistence/        # persistenza su PostgreSQL: consumer AMQP
├── api/                # REST API: storico, allerte attive, raccomandazioni
├── sensors-simulator/  # simulatori Python dei nodi IoT installati nelle parcelle del vigneto
├── dashboard/          # frontend HTML/JS + Chart.js
├── docs/uml/           # diagrammi UML rappresentativi
├── tests/
│   ├── load/           # test end-to-end di carico con misurazione di latenza e throughput (K6)
│   └── postman/        # test funzionale degli endpoint REST API (Postman/Newman)
```

N.B.: `certs/`, `secrets/` e `.env` (artefatti prodotti da `setup-credentials.sh` e `genera-certificati-tls.sh`) 
non sono tracciati da Git, in quanto vanno rigenerato su ogni macchina locale e non copiati da un'altra.