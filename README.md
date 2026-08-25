# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `PyYAML`, `python-dotenv`, `psycopg2-binary`, `pytest`

## Avvio dell'intero stack applicativo

```bash
chmod +x scripts/setup-credentials.sh   # solo la prima volta
./scripts/setup-credentials.sh          # genera credenziali locali casuali e chiede due percorsi utili al Tomcat
docker compose up -d
```

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
  presentare la credenziale reale per autenticarsi, non il suo hash.

Lo script è pensato per essere eseguito una sola volta: se viene rilanciato e trova già uno di
questi quattro artefatti, non tocca nulla e stampa le istruzioni per una rigenerazione completa
e coerente, nell'ordine corretto — prima `docker compose down -v` (mentre `.env` esiste ancora,
perché il servizio `tomcat` lo richiede anche solo per potersi fermare), poi la cancellazione
dei file, poi la rigenerazione. Se preferisci impostare le credenziali manualmente, copia
`.env.example` in `.env` e `secrets/*.txt.example` in `secrets/*.txt`, sostituendo i placeholder
con valori a tua scelta (inclusi i due percorsi assoluti).

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
  ricaricano da sole.
- `infra/tomcat/istanze-escluse.conf` — un'istanza per riga per escluderla dall'avvio, utile per
  isolare il troubleshooting su uno o più moduli senza fermare gli altri; di default è vuoto
  (tutte e cinque partono).

Porte esposte dal container `Tomcat`:

| Istanza | Porta |
|---|---|
| `decisionengine` | 8081 |
| `attuatori` | 8082 |
| `persistence` | 8083 |
| `api` | 8084 |
| `dashboard` | 8085 |

## Servizi esposti

| Servizio | Porta | Descrizione |
|---|---|---|
| RabbitMQ MQTT | 1883 | Endpoint per la pubblicazione dei sensori IoT |
| RabbitMQ AMQP | 5672 | Endpoint per i consumer applicativi |
| RabbitMQ Management UI | 15672 | Console web per management (credenziali generate da `setup-credentials.sh`) |
| PostgreSQL | 5432 | Persistenza delle misurazioni, allerte e trattamenti |

Lo schema sul database viene creato automaticamente al primo avvio da `infra/postgres/init/01_schema.sql`.

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── scripts/
│   └── setup-credentials.sh   # generazione credenziali locali
├── secrets/
│   ├── postgres_user.txt.example
│   └── postgres_password.txt.example
├── infra/
│   ├── rabbitmq/       # configurazione broker MQTT + definitions.json per la profilazione dell'utente admin
│   ├── postgres/       # schema database caricato all'inizializzazione di Docker Compose
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