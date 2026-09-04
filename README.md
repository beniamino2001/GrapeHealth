# GrapeHealth

GrapeHealth è un middleware per la viticoltura di precisione: raccoglie le letture di una rete di sensori IoT installati in vigneto (temperatura dell'aria e del suolo, umidità dell'aria e del suolo, bagnatura fogliare, precipitazioni, potenziale idrico dello stelo, velocità del vento, temperatura della bacca), le confronta con soglie tratte dalla letteratura scientifica su malattie della vite e stress climatico, e quando le condizioni lo richiedono genera un'allerta con una raccomandazione (per esempio un'irrigazione di soccorso in caso di stress idrico, o un trattamento fitosanitario in caso di rischio di peronospora). Il tutto è visibile in una dashboard web.

In questo repository i sensori sono simulati (non c'è un vigneto reale collegato), ma tutto il resto, ovvero la messaggistica, le regole, il salvataggio dei dati, l'interfaccia, funziona esattamente come farebbe con sensori veri.

## Come avviarlo

L'unico prerequisito manuale è avere il progetto clonato con `git clone https://github.com/beniamino2001/GrapeHealth` (non lo ZIP di GitHub: scaricato così non è una vera repository Git, e uno script più avanti ne ha bisogno per funzionare).

Da un terminale (per OS Windows installa e usa una distribuzione WSL), nella cartella GrapeHealth/ del progetto:
```bash
sh scripts/avvia-tutto.sh
```
Il primo avvio richiede qualche minuto, e fa tutto da solo: riconosce il sistema operativo e installa (o avvia, se già installato ma spento) tutto quello che serve (Python, OpenSSL, una JDK, Docker e Docker Compose), genera le credenziali locali (chiedendo due percorsi specifici sul tuo computer), i certificati per le connessioni cifrate, e avvia tutti i servizi. Alla fine, lo script conferma quando tutto è davvero pronto oppure, se qualcosa non funziona, si ferma e lo dice, senza lasciare l'infrastruttura avviata a metà.

Su Linux, se il tuo utente non fa già parte del gruppo `docker`, lo script ce lo aggiunge da sé e riprende subito, senza bisogno di un nuovo login (a meno che il tuo sistema non abbia lo strumento necessario per farlo su due piedi, nel qual caso te lo dice chiaramente: basta aprire un nuovo terminale e rilanciare).

## Come si usa

Una volta avviato, apri **`https://grapehealth.localhost`** nel browser: è la dashboard, con lo stato del vigneto simulato, le allerte attive e i grafici delle misurazioni. Non serve configurare nulla: quell'indirizzo funziona da solo su qualunque computer, senza bisogno di modificare file di sistema, e il certificato è già riconosciuto come attendibile dal browser (N.B.: Firefox fa eccezione in quanto usa un proprio archivio di certificati separato da quello di sistema, pertanto si consiglia la navigazione da un browser basato su Chromium oppure su Safari per macOS). Su WSL vale lo stesso ma un livello più in su: il browser che apri davvero è quello su Windows, e non dentro WSL, e lo script lo sa infatti importa la CA anche nello store dell'utente Windows corrente, senza bisogno di un passaggio manuale in più né di permessi da amministratore.

Per generare dati da vedere nella dashboard, in un altro terminale:
```bash
cd sensors-simulator
python3 -m venv .venv && . .venv/bin/activate && pip install --upgrade pip && pip install -U -r requirements.txt && python -m simulator.main --scenario ondata_di_calore --time-scale 2880
```
`--time-scale 2880` accelera il tempo simulato, così l'evoluzione delle condizioni del vigneto si vede in pochi minuti invece che in giorni. Gli scenari disponibili sono tre: `normale`, `stress_idrico` e `ondata_di_calore`.

Per fermare tutto: `docker compose down` (i dati restano) oppure `docker compose down -v` (riparte da zero al prossimo avvio).

## Uno sguardo all'infrastruttura

Il cuore del sistema è una coda di messaggistica (RabbitMQ) attraverso cui viaggiano le letture dei sensori: un modulo le valuta contro le regole fitosanitarie e climatiche e genera le allerte, un altro le salva su database (PostgreSQL), un altro simula l'attuazione delle raccomandazioni (per esempio, l'attivazione di un impianto di irrigazione), un ultimo modulo espone tutto tramite un'API web che la dashboard consulta. Un reverse proxy (nginx) è l'unico punto d'ingresso raggiungibile dall'esterno, con lo stesso schema (nome a dominio e certificato) che avrebbe un servizio pubblicato davvero su Internet.

Ogni connessione fra i vari pezzi è cifrata, comprese quelle puramente interne fra i moduli, non solo quella verso il browser.

## Requisiti in dettaglio

L'unico prerequisito manuale è il repository clonato con `git clone` (non lo ZIP scaricabile da GitHub). Tutto il resto (Docker e Docker Compose, Python 3, OpenSSL, una JDK con `keytool`) `scripts/avvia-tutto.sh` lo installa da sé se manca, riconoscendo automaticamente il sistema operativo (macOS, Debian/Ubuntu e derivate come Linux Mint, Fedora, Arch Linux e derivate come Manjaro). Su una distribuzione non riconosciuta lo script si ferma con un messaggio chiaro invece di indovinare nomi di pacchetto mai verificati: in quel caso, o se preferisci comunque installare tutto a mano prima di lanciarlo, i comandi sotto sono esattamente quelli che lo script userebbe da solo.
#### macOS — Homebrew
Se non hai già Homebrew, installalo seguendo le istruzioni ufficiali — è l'unica cosa che lo script non installa da sé.
```bash
brew update
brew install python openssl
brew install --cask temurin
brew install --cask docker
```
#### Debian / Ubuntu
```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip openssl ca-certificates openjdk-21-jdk docker.io docker-compose-v2
sudo usermod -aG docker "$USER"
```
Dopo l'ultimo comando serve un nuovo login (oppure una nuova finestra di terminale) perché l'appartenenza al gruppo `docker` valga — `avvia-tutto.sh` lo fa da sé senza bisogno di questo passaggio, usandolo solo se lanci i comandi Docker a mano prima di eseguirlo.
#### Fedora
```bash
sudo dnf install -y python3 python3-pip python3-devel openssl ca-certificates java-latest-openjdk-devel moby-engine docker-compose
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```
Due dettagli non ovvi in queste righe: `java-latest-openjdk-devel`, non `java-21-openjdk` — a Fedora corrente la versione 21 non è più tra i pacchetti disponibili, ed è comunque `-devel` (non la sola variante runtime) a fornire `keytool`; il progetto costruisce le proprie immagini con Java 21 al proprio interno, quindi sull'host basta una JDK recente qualsiasi. E Docker: Fedora non ha un pacchetto chiamato `docker`, si chiama `moby-engine`.
#### Arch Linux
```bash
sudo pacman -Syu --needed python python-pip openssl ca-certificates jdk21-openjdk docker docker-compose
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```
`python-virtualenv` non serve: Python 3.3+ include già il modulo `venv` usato da questo progetto (`python3 -m venv`) — è uno strumento di terze parti diverso, non necessario qui.
### Altri sistemi Unix/Linux
`avvia-tutto.sh` riconosce solo le famiglie sopra (comprese le derivate, tramite `ID_LIKE`); su qualunque altro sistema Unix installa tramite il package manager disponibile i seguenti pacchetti:
| Componente                   | Scopo                                              |
| ---------------------------- | -------------------------------------------------- |
| `python3`                    | esecuzione del simulatore dei sensori              |
| `python3-venv` / equivalente | creazione dell'ambiente virtuale Python            |
| `python3-pip` / equivalente  | installazione delle dipendenze Python              |
| `openssl`                    | generazione e gestione dei certificati             |
| JDK                          | fornisce `keytool`, utilizzato per i keystore Java |
| Docker Engine                | esecuzione dei container                           |
| Docker Compose               | avvio e collegamento dei servizi                   |
### Verifica dell'installazione
Non necessaria prima di `sh scripts/avvia-tutto.sh` (lo script verifica da sé cosa manca), utile se hai installato tutto a mano o vuoi solo controllare lo stato del sistema:
```bash
python3 --version
openssl version
java -version
keytool -help
docker --version
docker compose version
```

## Struttura del repository

```
GrapeHealth/
├── docker-compose.yml    # definisce e collega tutti i servizi
├── scripts/              # generazione di credenziali e certificati e avvio infrastruttura e applicazioni con un comando
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