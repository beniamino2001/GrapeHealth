# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `pandas`, `numpy`
- Java 21 e Maven

## Avvio dell'infrastruttura di base

```bash
cp .env.example .env
docker compose up -d
```
