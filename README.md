# GrapeHealth

GrapeHealth è un middleware per la viticoltura di precisione: raccoglie le letture di una rete di sensori IoT installati in vigneto (temperatura dell'aria e del suolo, umidità dell'aria e del suolo, bagnatura fogliare, precipitazioni, potenziale idrico dello stelo, velocità del vento, temperatura della bacca), le confronta con soglie tratte dalla letteratura scientifica su malattie della vite e stress climatico, e quando le condizioni lo richiedono genera un'allerta con una raccomandazione — per esempio un'irrigazione di soccorso in caso di stress idrico, o un trattamento fitosanitario in caso di rischio di peronospora. Il tutto è visibile in una dashboard web.

In questo repository i sensori sono simulati (non c'è un vigneto reale collegato), ma tutto il resto — la messaggistica, le regole, il salvataggio dei dati, l'interfaccia — funziona esattamente come farebbe con sensori veri.

## Come avviarlo

Serve avere installati Docker, Python 3 e una JDK (per i dettagli precisi vedi più sotto). Il progetto va scaricato clonandolo con `git clone`, non con lo ZIP di GitHub.

Da un terminale, nella cartella del progetto:
```bash
chmod +x infra/tomcat/*.sh scripts/*.sh   # solo la prima volta
sh scripts/avvia-tutto.sh
```
> **Nota:** Docker deve essere installato e avviato prima di eseguire `scripts/avvia-tutto.sh`. Su Linux, l'utente che esegue il progetto deve inoltre poter utilizzare Docker senza `sudo` oppure i comandi Docker devono essere eseguiti con i privilegi appropriati.

Il primo avvio richiede qualche minuto: genera le credenziali locali (chiedendo due percorsi sul tuo computer locale), i certificati per le connessioni cifrate, poi compila e avvia tutti i servizi. Alla fine, lo script conferma quando tutto è davvero pronto — se qualcosa non funziona, si ferma da solo e lo dice, senza lasciare l'infrastruttura a metà.

## Come si usa

Una volta avviato, apri **`https://grapehealth.localhost`** nel browser: è la dashboard, con lo stato del vigneto simulato, le allerte attive e i grafici delle misurazioni. Non serve configurare nulla: quell'indirizzo funziona da solo su qualunque computer, senza bisogno di modificare file di sistema, e il certificato è già riconosciuto come attendibile dal browser (Firefox fa eccezione: usa un proprio archivio di certificati separato da quello di sistema).

Per generare dati da vedere nella dashboard, in un altro terminale:
```bash
cd sensors-simulator
python3 -m venv .venv && . .venv/bin/activate && pip install --upgrade pip && pip install -U -r requirements.txt && python -m simulator.main --scenario ondata_di_calore --time-scale 2880
```
`--time-scale 2880` accelera il tempo simulato, così l'evoluzione delle condizioni del vigneto si vede in pochi minuti invece che in giorni. Gli scenari disponibili sono tre: `normale`, `stress_idrico` e `ondata_di_calore`.

Per fermare tutto: `docker compose down` (i dati restano) oppure `docker compose down -v` (riparte da zero al prossimo avvio).

## Uno sguardo sotto il cofano

Il cuore del sistema è una coda di messaggistica (RabbitMQ) attraverso cui viaggiano le letture dei sensori: un modulo le valuta contro le regole fitosanitarie e climatiche e genera le allerte, un altro le salva su database (PostgreSQL), un altro simula l'attuazione delle raccomandazioni (per esempio, l'attivazione di un impianto di irrigazione), un ultimo modulo espone tutto tramite un'API web che la dashboard consulta. Un reverse proxy (nginx) è l'unico punto d'ingresso raggiungibile dall'esterno, con lo stesso schema — nome a dominio e certificato — che avrebbe un servizio pubblicato davvero su Internet.

Ogni connessione fra i vari pezzi è cifrata, comprese quelle puramente interne fra i moduli, non solo quella verso il browser.

## Requisiti in dettaglio

- Il repository clonato con `git clone` (non lo ZIP scaricabile da GitHub)
- Docker e Docker Compose
- Python 3.x
- OpenSSL e una JDK con `keytool`

I comandi riportati sotto installano tutti i prerequisiti principali usando il package manager del sistema.
#### macOS — Homebrew
Se non hai già Homebrew, installalo seguendo le istruzioni ufficiali.
Poi:
```bash
brew update
brew install git python openssl
brew install --cask temurin
```
Docker Desktop può essere installato con:
```bash
brew install --cask docker
```
Dopo l'installazione, avvia Docker Desktop dal menu Applicazioni oppure con:
```bash
open -a Docker
```
#### Debian / Ubuntu
Su Debian e Ubuntu:
```bash
sudo apt update
sudo apt install -y git python3 python3-venv python3-pip openssl ca-certificates openjdk-21-jdk
```
Docker può essere installato seguendo il repository ufficiale Docker oppure, su sistemi dove è sufficiente una versione disponibile nei repository della distribuzione:
```bash
sudo apt install -y docker.io docker-compose-v2
```
Per permettere al proprio utente di usare Docker senza `sudo`:
```bash
sudo usermod -aG docker "$USER"
```
Dopo questo comando è necessario effettuare nuovamente il login (oppure riavviare la sessione).
#### Fedora
Su Fedora:
```bash
sudo dnf install -y git python3 python3-pip python3-devel openssl ca-certificates java-latest-openjdk-devel
```
Nota: usa `java-latest-openjdk-devel`, non `java-21-openjdk` — a Fedora corrente, la versione 21 non è più tra i pacchetti disponibili, ed è comunque `-devel` (non la sola variante runtime) a fornire `keytool`. Il progetto costruisce le proprie immagini con Java 21 al proprio interno: qui basta una JDK recente qualsiasi.
Installa quindi Docker (Fedora non ha un pacchetto chiamato `docker`: si chiama `moby-engine`):
```bash
sudo dnf install -y moby-engine docker-compose
```
Avvia il servizio:
```bash
sudo systemctl enable --now docker
```
Per utilizzare Docker senza `sudo`:
```bash
sudo usermod -aG docker "$USER"
```
È necessario effettuare nuovamente il login dopo aver aggiunto l'utente al gruppo `docker`.
#### Arch Linux
Su Arch Linux:
```bash
sudo pacman -Syu --needed git python python-pip openssl ca-certificates jdk21-openjdk docker docker-compose
```
`python-virtualenv` non serve: Python 3.3+ include già il modulo `venv` usato da questo progetto (`python3 -m venv`), è uno strumento di terze parti diverso e non necessario qui.
Avvia Docker:
```bash
sudo systemctl enable --now docker
```
Per utilizzare Docker senza `sudo`:
```bash
sudo usermod -aG docker "$USER"
```
Effettua nuovamente il login dopo aver aggiunto l'utente al gruppo `docker`.
### Altri sistemi Unix/Linux
Su altre distribuzioni Linux o sistemi Unix, installa tramite il package manager disponibile i seguenti pacchetti:
| Componente                   | Scopo                                              |
| ---------------------------- | -------------------------------------------------- |
| `git`                        | clonazione del repository                          |
| `python3`                    | esecuzione del simulatore dei sensori              |
| `python3-venv` / equivalente | creazione dell'ambiente virtuale Python            |
| `python3-pip` / equivalente  | installazione delle dipendenze Python              |
| `openssl`                    | generazione e gestione dei certificati             |
| JDK                          | fornisce `keytool`, utilizzato per i keystore Java |
| Docker Engine                | esecuzione dei container                           |
| Docker Compose               | avvio e collegamento dei servizi                   |
### Verifica dell'installazione
Dopo aver installato i prerequisiti, verifica che i comandi seguenti siano disponibili:
```bash
git --version
python3 --version
openssl version
java -version
keytool -help
docker --version
docker compose version
```
Se tutti i comandi vengono riconosciuti e rispettano le versioni indicate, puoi procedere con l'avvio del progetto.

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml    # definisce e collega tutti i servizi
├── scripts/              # generazione di credenziali e certificati, avvio con un comando
├── infra/                # configurazione di PostgreSQL, RabbitMQ, Tomcat, nginx
├── backend/               # valuta le regole fitosanitarie/climatiche e genera le allerte
├── attuatori/              # simula l'esecuzione delle raccomandazioni
├── persistence/            # salva letture, allerte e trattamenti su database
├── api/                    # espone i dati alla dashboard
├── dashboard/              # l'interfaccia web
├── sensors-simulator/       # simula i sensori IoT nel vigneto
├── docs/uml/                # diagrammi UML del progetto
└── tests/                   # test di carico (K6) e degli endpoint (Postman)
```

`.env`, `secrets/`, `certs/` e `infra/rabbitmq/definitions.json` contengono credenziali e certificati generati in locale da `scripts/avvia-tutto.sh`: non sono su Git, ogni copia del progetto se li genera da sé.