# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `pandas`, `numpy`

## Avvio dell'infrastruttura di base

```bash
cp .env.example .env
docker compose up -d
```

Servizi esposti:

| Servizio | Porta | Descrizione |
|---|---|---|
| RabbitMQ MQTT | 1883 | Endpoint per la pubblicazione dei sensori IoT |
| RabbitMQ AMQP | 5672 | Endpoint per i consumer applicativi |
| RabbitMQ Management UI | 15672 | Console web per management (utente/password in `.env`) |
| PostgreSQL | 5432 | Persistenza delle misurazioni, allerte e trattamenti |

Lo schema sul database viene creato automaticamente al primo avvio da `infra/postgres/init/01_schema.sql`.

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml
├── infra/
│   ├── rabbitmq/       # configurazione broker MQTT
│   └── postgres/       # schema iniziale
├── backend/            # API Spring Boot
├── sensors-simulator/  # simulatori Python dei nodi IoT installati nelle parcelle del vigneto
├── dashboard/          # frontend HTML/JS + Chart.js
├── docs/uml/           # diagrammi UML rappresentativi
└── tests/load/         # test prestazionali
```