#!/usr/bin/env sh
# Avvia l'intero stack con un solo comando, incatenando nell'ordine imposto da un vincolo
# reale e non aggirabile: docker-compose.yml usa "${VAR}" per porte, percorsi e il segreto
# del keystore, letti da .env — senza quel file Compose fallisce già nella sola lettura
# della propria configurazione (verificato: "invalid spec: :...: empty section between
# colons"), prima ancora di provare a costruire una sola immagine. "docker compose up
# -d --build" da solo, su una macchina che non ha mai eseguito nulla di questo repository,
# non può quindi bastare: prima delle credenziali/certificati TLS servono anche Python,
# OpenSSL, una JDK e Docker stesso — installati qui sotto se mancanti, non solo verificati.
#
# Uso: sh scripts/avvia-tutto.sh (dalla radice del repository, dopo un git clone — non
# uno ZIP scaricato, scripts/setup-credenziali.sh richiede una vera repository Git). Git
# non è fra le dipendenze gestite da questo script: chi arriva a eseguirlo lo ha già, per
# definizione, dato che è così che si ottiene questo stesso file.
set -eu
cd "$(dirname "$0")/.."

# Sezione di installazione delle dipendenze — pensata per essere incollata all'inizio di
# scripts/avvia-tutto.sh, prima della generazione di credenziali/certificati. Git non è fra
# le dipendenze gestite qui: chi arriva a eseguire questo script ha già clonato il
# repository, quindi lo ha già per definizione.

rileva_sistema() {
  case "$(uname -s)" in
    Darwin) echo "macos"; return 0 ;;
    Linux) ;;
    *) echo "sconosciuto"; return 0 ;;
  esac
  if [ ! -f /etc/os-release ]; then
    echo "sconosciuto"; return 0
  fi
  ID=""; ID_LIKE=""
  # shellcheck disable=SC1091
  . /etc/os-release
  for candidato in "$ID" $ID_LIKE; do
    case "$candidato" in
      ubuntu|debian) echo "debian"; return 0 ;;
      fedora) echo "fedora"; return 0 ;;
      arch) echo "arch"; return 0 ;;
    esac
  done
  echo "sconosciuto"
}

SISTEMA="$(rileva_sistema)"
if [ "$SISTEMA" = "sconosciuto" ]; then
  echo "ATTENZIONE: distribuzione non riconosciuta automaticamente (né Debian/Ubuntu," >&2
  echo "né Fedora, né Arch Linux, né una loro derivata). Installa manualmente le" >&2
  echo "dipendenze elencate nel README (sezione \"Altri sistemi Unix/Linux\") e rilancia." >&2
  exit 1
fi

# WSL è ortogonale a SISTEMA, non un'alternativa: la distribuzione Linux dentro WSL è
# comunque una delle tre sopra (qui serve solo a decidere il pacchetto Python/OpenSSL/JDK
# giusto), ma Docker è diverso — l'approccio standard su WSL è Docker Desktop per Windows
# con l'integrazione WSL2, non un motore Docker separato dentro la distribuzione Linux:
# due Docker indipendenti che non si parlano fra loro sarebbero più confusione che aiuto.
IN_WSL=0
if [ "$SISTEMA" != "macos" ] && grep -qi microsoft /proc/version 2>/dev/null; then
  IN_WSL=1
fi

# Homebrew, a differenza di apt/dnf/pacman, non fa parte del sistema operativo: su una
# macOS pulita potrebbe non esserci ancora, e "brew install ..." fallirebbe con un
# generico "comando non trovato" invece di dire cosa manca davvero e dove procurarselo.
if [ "$SISTEMA" = "macos" ] && ! command -v brew >/dev/null 2>&1; then
  echo "ERRORE: Homebrew non risulta installato. Installalo da https://brew.sh e rilancia." >&2
  exit 1
fi

# Root non ha bisogno di sudo — e su molti sistemi minimali (container, alcune VM) sudo
# potrebbe non essere installato affatto: usarlo comunque romperebbe lo script proprio dove
# servirebbe di meno, dato che da root ogni comando sotto funziona già senza.
if [ "$(id -u)" -eq 0 ]; then
  SUDO=""
else
  if ! command -v sudo >/dev/null 2>&1; then
    echo "ERRORE: questo script deve girare come root oppure avere \"sudo\" disponibile." >&2
    exit 1
  fi
  SUDO="sudo"
fi

installa_python() {
  if command -v python3 >/dev/null 2>&1 && python3 -c "import venv" >/dev/null 2>&1; then
    echo "  - Python 3 (con venv): già presente."
    return 0
  fi
  echo "  - Python 3: installo..."
  case "$SISTEMA" in
    macos)  brew install python ;;
    debian) $SUDO apt-get update -qq || true; $SUDO apt-get install -y python3 python3-venv python3-pip ;;
    fedora) $SUDO dnf install -y python3 python3-pip python3-devel ;;
    arch)   $SUDO pacman -Sy --needed --noconfirm python python-pip ;;
  esac
}

installa_openssl() {
  if command -v openssl >/dev/null 2>&1; then
    echo "  - OpenSSL: già presente."
    return 0
  fi
  echo "  - OpenSSL: installo..."
  case "$SISTEMA" in
    macos)  brew install openssl ;;
    debian) $SUDO apt-get update -qq || true; $SUDO apt-get install -y openssl ca-certificates ;;
    fedora) $SUDO dnf install -y openssl ca-certificates ;;
    arch)   $SUDO pacman -Sy --needed --noconfirm openssl ca-certificates ;;
  esac
}

installa_jdk() {
  if command -v keytool >/dev/null 2>&1; then
    echo "  - JDK (keytool): già presente."
    return 0
  fi
  echo "  - JDK: installo..."
  case "$SISTEMA" in
    macos)  brew install --cask temurin ;;
    debian) $SUDO apt-get update -qq || true; $SUDO apt-get install -y openjdk-21-jdk ;;
    fedora) $SUDO dnf install -y java-latest-openjdk-devel ;;
    arch)   $SUDO pacman -Sy --needed --noconfirm jdk21-openjdk ;;
  esac
}

installa_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    echo "  - Docker e Docker Compose: già presenti."
    return 0
  fi
  if [ "$IN_WSL" -eq 1 ]; then
    echo "  - Docker/Docker Compose: non presenti. Installo Docker Desktop per Windows"
    echo "    (l'integrazione WSL2 lo rende usabile da qui, non un motore separato dentro"
    echo "    questa distribuzione)..."
    if ! command -v winget.exe >/dev/null 2>&1; then
      echo "ERRORE: winget.exe non raggiungibile da questa sessione WSL. Installa Docker" >&2
      echo "Desktop da https://www.docker.com/products/docker-desktop, poi abilita la sua" >&2
      echo "integrazione con questa distribuzione (Impostazioni -> Resources -> WSL" >&2
      echo "Integration) e rilancia questo script." >&2
      exit 1
    fi
    # L'installazione di software su Windows, a differenza dell'import di un certificato
    # nello store utente (vedi genera-certificati-tls.sh), non ha un equivalente "senza
    # elevazione" affidabile: il manifesto winget di Docker Desktop dichiara solo lo scope
    # di macchina, che richiede permessi di amministratore. Se manca quell'elevazione qui
    # non c'è modo di ottenerla in automatico senza interrompere lo script con un prompt
    # interattivo di Windows: meglio dirlo chiaramente e uscire che restare bloccati lì.
    if ! winget.exe install -e --id Docker.DockerDesktop --silent \
         --accept-package-agreements --accept-source-agreements; then
      echo "ERRORE: installazione automatica di Docker Desktop fallita — richiede permessi" >&2
      echo "di amministratore Windows che questa sessione non ha. Installalo tu: apri" >&2
      echo "PowerShell come amministratore e lancia" >&2
      echo "  winget install -e --id Docker.DockerDesktop" >&2
      echo "oppure scaricalo da https://www.docker.com/products/docker-desktop. Poi" >&2
      echo "rilancia questo script." >&2
      exit 1
    fi
    return 0
  fi
  echo "  - Docker/Docker Compose: installo..."
  case "$SISTEMA" in
    macos)  brew install --cask docker ;;
    debian) $SUDO apt-get update -qq || true; $SUDO apt-get install -y docker.io docker-compose-v2 ;;
    fedora) $SUDO dnf install -y moby-engine docker-compose ;;
    arch)   $SUDO pacman -Sy --needed --noconfirm docker docker-compose ;;
  esac
}

avvia_docker() {
  if docker info >/dev/null 2>&1; then
    echo "  - Servizio Docker: già in esecuzione."
    return 0
  fi
  if [ "$IN_WSL" -eq 1 ]; then
    echo "  - Avvio Docker Desktop (Windows)..."
    # Percorso di installazione standard: se l'utente l'ha installato altrove questo
    # tentativo silenziosamente non fa nulla, e l'attesa sotto se ne accorge comunque —
    # dice chiaramente che non è partito invece di fingere un successo.
    docker_desktop_exe="/mnt/c/Program Files/Docker/Docker/Docker Desktop.exe"
    if [ -x "$docker_desktop_exe" ]; then
      "$docker_desktop_exe" >/dev/null 2>&1 &
    fi
    TENTATIVI=0
    while ! docker info >/dev/null 2>&1; do
      TENTATIVI=$((TENTATIVI + 1))
      if [ "$TENTATIVI" -ge 60 ]; then
        echo "ERRORE: Docker Desktop non risulta pronto dopo 120s. Avvialo da Windows e" >&2
        echo "verifica che l'integrazione con questa distribuzione WSL sia abilitata" >&2
        echo "(Impostazioni -> Resources -> WSL Integration), poi rilancia questo script." >&2
        exit 1
      fi
      sleep 2
    done
    return 0
  fi
  case "$SISTEMA" in
    macos)
      echo "  - Avvio Docker Desktop..."
      open -a Docker
      TENTATIVI=0
      while ! docker info >/dev/null 2>&1; do
        TENTATIVI=$((TENTATIVI + 1))
        if [ "$TENTATIVI" -ge 60 ]; then
          echo "ERRORE: Docker Desktop non risulta pronto dopo 120s. Avvialo manualmente e rilancia." >&2
          exit 1
        fi
        sleep 2
      done
      ;;
    debian|fedora|arch)
      echo "  - Avvio il servizio Docker..."
      if ! $SUDO systemctl enable --now docker 2>/dev/null; then
        echo "ERRORE: impossibile avviare il servizio Docker con systemctl." >&2
        echo "Su un sistema senza systemd attivo (alcuni container) va avviato" >&2
        echo "manualmente secondo le indicazioni della tua distribuzione." >&2
        exit 1
      fi
      if [ "$SUDO" = "sudo" ] && ! groups "$(whoami)" | grep -qw docker; then
        echo "  - Aggiungo $(whoami) al gruppo docker..."
        $SUDO usermod -aG docker "$(whoami)"
        # "sg" non è garantito presente ovunque: su Arch Linux è stato rimosso dal
        # pacchetto shadow in alcuni aggiornamenti (segnalato sul forum ufficiale),
        # senza sostituto immediato nello stesso pacchetto. Senza "sg" non c'è modo di
        # applicare il nuovo gruppo alla sessione corrente senza un logout — meglio
        # dirlo chiaramente che fallire con un criptico "sg: command not found".
        if command -v sg >/dev/null 2>&1; then
          echo "  - Riavvio con il nuovo gruppo attivo, senza bisogno di un nuovo login..."
          exec sg docker -c "sh \"$0\""
        else
          echo "Aggiunto al gruppo docker, ma serve un nuovo login (o un nuovo terminale)" >&2
          echo "perché valga in questa sessione: \"sg\" non è disponibile su questo sistema" >&2
          echo "per applicarlo subito. Apri un nuovo terminale e rilancia questo script." >&2
          exit 1
        fi
      fi
      ;;
  esac
}


echo "=== 1/4: dipendenze (sistema rilevato: $SISTEMA) ==="
installa_python
installa_openssl
installa_jdk
installa_docker
avvia_docker

chmod +x infra/tomcat/*.sh scripts/*.sh

echo
echo "=== 2/4: credenziali (.env, Docker secrets) ==="
sh scripts/setup-credenziali.sh

echo
echo "=== 3/4: certificati TLS locali ==="
sh scripts/genera-certificati-tls.sh

echo
echo "=== 4/4: avvio dello stack pulito ==="
docker compose down -v && docker compose up -d --build

# "docker compose up -d" ritorna successo appena i container sono avviati, non quando sono
# davvero operativi. postgres/rabbitmq hanno un healthcheck che "depends_on" rispetta
# davvero; tomcat no — un solo healthcheck non potrebbe rappresentare lo stato di cinque
# istanze indipendenti nello stesso container, e senza un healthcheck qui sotto questo
# script direbbe "Stack avviato" anche con Tomcat già uscito un istante dopo (verificato:
# un container che esce 2 secondi dopo "Started" non impedisce comunque a "up -d" di
# ritornare 0). Si attende quindi, fino a un massimo pari al timeout già concesso al
# build Maven in start-instances.sh, che compaia nei log di tomcat lo stesso messaggio
# che quello script stampa a fine avvio riuscito — uscendo prima se compare, e
# interrompendosi subito, senza aspettare il resto del timeout, se un container esce.
echo
echo "Verifico che Tomcat completi l'avvio..."
TIMEOUT_SECONDI=600
INTERVALLO=5
ATTESA=0
AVVIATO=0
while [ "$ATTESA" -lt "$TIMEOUT_SECONDI" ]; do
  # Niente ancoraggio "^tomcat" davanti al testo cercato: sembrava una precauzione in più,
  # invece era il bug — "docker compose logs tomcat" prefissa ogni riga con il vero nome
  # del container quando "container_name" è impostato esplicitamente (qui grapehealth-tomcat,
  # non "tomcat"), quindi quel prefisso non ha mai trovato corrispondenza. Verificato
  # riproducendo l'esatta configurazione reale (stesso container_name) con un demone Docker
  # vero: il pattern vecchio non trovava mai il messaggio anche quando era presente per
  # davvero, esattamente il sintomo osservato. "docker compose logs tomcat" filtra già da
  # solo al solo servizio tomcat: non serve verificare anche il prefisso della riga.
  if docker compose logs tomcat 2>/dev/null | grep -q "Istanze avviate:"; then
    AVVIATO=1
    break
  fi
  USCITI=$(docker compose ps --status=exited --format '{{.Name}}' 2>/dev/null || true)
  if [ -n "$USCITI" ]; then
    echo "ERRORE: uno o più container sono usciti durante l'avvio:" >&2
    echo "$USCITI" >&2
    echo "Controlla il motivo con \"docker compose logs <nome-servizio>\" prima di riprovare." >&2
    exit 1
  fi
  # "docker compose up -d" non aspetta nemmeno l'healthcheck di postgres/rabbitmq stessi
  # prima di ritornare (verificato: un healthcheck destinato a non passare mai non ha
  # impedito a "up -d" di tornare in un secondo) — solo blocca la CREAZIONE di tomcat, che
  # dipende da "condition: service_healthy" su entrambi. Se uno dei due restasse bloccato
  # "unhealthy" per sempre, senza questo controllo il ciclo aspetterebbe l'intero timeout
  # sperando di trovare tomcat nei log, e il messaggio finale punterebbe a tomcat invece
  # che alla causa vera. postgres/rabbitmq "in salute" restano comunque in esecuzione anche
  # se il proprio healthcheck fallisce (non esce, resta semplicemente "unhealthy"): il
  # controllo sugli "usciti" sopra non lo vedrebbe mai, serve un controllo separato.
  NON_SANI=$(docker compose ps --format '{{.Name}} {{.Health}}' 2>/dev/null | awk '$2 == "unhealthy" {print $1}' || true)
  if [ -n "$NON_SANI" ]; then
    echo "ERRORE: uno o più container non superano il proprio healthcheck, tomcat resta in attesa e non partirà mai:" >&2
    echo "$NON_SANI" >&2
    echo "Controlla il motivo con \"docker compose logs <nome-servizio>\" prima di riprovare." >&2
    exit 1
  fi
  sleep "$INTERVALLO"
  ATTESA=$((ATTESA + INTERVALLO))
done
if [ "$AVVIATO" -ne 1 ]; then
  echo "ERRORE: Tomcat non ha completato l'avvio entro ${TIMEOUT_SECONDI}s." >&2
  echo "Controlla \"docker compose logs tomcat\" per capire a che punto si è fermato." >&2
  exit 1
fi

echo
echo "Stack avviato, Tomcat operativo su tutte e cinque le istanze."
echo "\"docker compose ps\" per lo stato dei container."