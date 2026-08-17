# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `PyYAML`, `python-dotenv`, `psycopg2-binary`, `pytest`

## Avvio dell'infrastruttura di base

```bash
chmod +x scripts/setup-credentials.sh   # solo la prima volta
./scripts/setup-credentials.sh          # genera credenziali locali casuali
docker compose up -d
```

`setup-credentials.sh` genera password casuali forti e le scrive in tre punti distinti (non tracciati da Git):

- `secrets/postgres_user.txt` / `postgres_password.txt`, letti dal container PostgreSQL tramite
  Docker secrets (`POSTGRES_USER_FILE`/`POSTGRES_PASSWORD_FILE`);
- `infra/rabbitmq/definitions.json`, letto dal container RabbitMQ al boot
  (`management.load_definitions`): contiene l'utente amministrativo con la password **già
  hashata** (`rabbitmqctl hash_password`), non in chiaro;
- `.env`, letto dalle app Spring Boot locali deployate sulle relative istanze Tomcat tramite `setenv.sh` 
  per autenticarsi come client AMQP/JDBC — qui la password resta necessariamente in chiaro, perché 
  un client deve sempre presentare la credenziale reale per autenticarsi, non il suo hash.

Lo script è pensato per essere eseguito una sola volta: se viene rilanciato e trova già uno di questi tre
artefatti, non tocca nulla e stampa le istruzioni per una rigenerazione completa e coerente
(comprensiva di `docker compose down -v`, necessario perché i container non aggiornano mai un
utente già esistente in base a nuove credenziali). Se preferisci impostare le credenziali
manualmente, copia `.env.example` in `.env` e `secrets/*.txt.example` in `secrets/*.txt`,
sostituendo i placeholder con valori a tua scelta.

Servizi esposti:

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
│   └── postgres/       # schema database caricato all'inizializzazione di Docker Compose
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