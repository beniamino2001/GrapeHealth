# GrapeHealth

Realizzazione di un middleware di messaggistica asincrona per la viticoltura di precisione: gestione dei trattamenti fitosanitari e monitoraggio dello stress idrico/termico della vite tramite rete di sensori IoT simulati.

## Requisiti

- Questo repository clonato con `git clone` (non scaricato come ZIP): `scripts/setup-credenziali.sh`
  usa `git rev-parse --show-toplevel` per individuare la propria posizione in modo indipendente
  dalla cartella da cui viene lanciato, e senza una vera repository Git fallisce subito con un
  errore esplicito piuttosto che indovinare un percorso sbagliato.
- Docker e Docker Compose
- Python 3.x con `paho-mqtt`, `PyYAML`, `python-dotenv`, `psycopg2-binary`, `pytest`
- OpenSSL e una JDK con `keytool`

## Avvio dell'intero stack applicativo da CLI in locale

Un solo comando (dopo il primo `chmod`) dentro la root della cartella GrapeHealth, che incatena i passi sotto nell'ordine
in cui devono avvenire:
```bash
chmod +x infra/tomcat/*.sh scripts/*.sh   # solo la prima volta
sh scripts/avvia-tutto.sh
```
Oppure, un passo alla volta:
```bash
chmod +x infra/tomcat/*.sh scripts/*.sh   # solo la prima volta
sh scripts/setup-credenziali.sh          # genera credenziali locali casuali e chiede due percorsi utili al Tomcat
sh scripts/genera-certificati-tls.sh    # genera una CA locale e i certificati TLS per i servizi infrastrutturali
docker compose up -d --build
```

N.B.: l'ordine di esecuzione conta poichè `genera-certificati-tls.sh` legge la password del keystore Java da `.env`, generata dal primo script — invertirli produce un errore esplicito.

`setup-credenziali.sh` genera password casuali forti e chiede due percorsi assoluti su 
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
container come bind mount. Chi clona il repository non deve prepararle a mano. A ogni container
ricreato da zero (dopo un `docker compose down`, con o senza `-v`: mai dopo un semplice `stop`/
`restart`, che riusa lo stesso container) i quattro moduli Spring Boot vengono ricompilati da
zero (`mvn package`, codice sorgente incluso nell'immagine, non solo il WAR già pronto) e tutte
e cinque le istanze rigenerate in `TOMCAT_INSTANCES_DIR` con il risultato — struttura di cartelle,
`server.xml` con la porta giusta, il WAR appena compilato per le quattro istanze Spring Boot o,
per `dashboard`, i soli file statici (`package.json` e `*.test.js` esclusi, servono solo alla
suite di test locale, mai all'istanza servita) — anche se in quella cartella c'era già qualcosa
da un avvio precedente. Un semplice riavvio dello stesso container, invece, non ricompila né
rigenera nulla: usa quello che c'è già, avvio rapido invece che un nuovo giro di build Maven.

- `infra/tomcat/Dockerfile` — parte da `tomcat:11-jdk21-temurin-jammy` con un Python minimale
  aggiunto solo per eseguire `sensors-simulator/scripts/init_nodi_db.py`, un'installazione Maven
  prelevata (non eseguita) dall'immagine ufficiale `maven:3.9-eclipse-temurin-21`, il codice
  sorgente dei quattro moduli Spring Boot, i file della dashboard e i due template
  (`infra/tomcat/server.xml.template`, `infra/tomcat/setenv.sh.template`) pronti per il
  provisioning delle istanze. La compilazione vera e propria avviene a runtime, non qui — vedi sotto.
- `infra/tomcat/start-instances.sh` — l'entrypoint: crea (o rigenera) e avvia le cinque istanze in
  parallelo dalla `CATALINA_HOME` condivisa, ciascuna con la propria `CATALINA_BASE`. Alla prima
  esecuzione dopo un `docker compose down` (con o senza `-v`, mai dopo un semplice `stop`) pulisce
  i log delle app Spring Boot, ricompila i quattro moduli con `mvn package` e rigenera tutte e
  cinque le istanze in `TOMCAT_INSTANCES_DIR` da zero — anche se contenevano già qualcosa da un
  avvio precedente, dato che è un bind mount e sopravvive a `-v` per conto proprio. Un riavvio
  dello stesso container, invece, non ricompila né rigenera nulla. Sincronizza inoltre `nodo_sensore`
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
| RabbitMQ Management UI | 15672 / 15671 (TLS) | Console web per management (credenziali generate da `setup-credenziali.sh`); entrambe le porte restano attive, a differenza di AMQP/MQTT |
| PostgreSQL (TLS obbligatorio) | 5432 | Persistenza delle misurazioni, allerte e trattamenti |

Lo schema sul database viene creato automaticamente al primo avvio da `infra/postgres/init/01_schema.sql`.

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── scripts/
│   ├── setup-credenziali.sh        # generazione credenziali locali + password keystore TLS
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

N.B.: `certs/`, `secrets/`, `infra/rabbitmq/definitions.json` e `.env` (artefatti prodotti da `setup-credenziali.sh` e `genera-certificati-tls.sh`) 
non sono tracciati da Git, in quanto vanno rigenerato su ogni macchina locale e non copiati da un'altra.