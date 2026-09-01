#!/usr/bin/env sh
# Avvia l'intero stack con un solo comando, incatenando nell'ordine imposto da un vincolo
# reale e non aggirabile: docker-compose.yml usa "${VAR}" per porte, percorsi e il segreto
# del keystore, letti da .env — senza quel file Compose fallisce già nella sola lettura
# della propria configurazione (verificato: "invalid spec: :...: empty section between
# colons"), prima ancora di provare a costruire una sola immagine. "docker compose up
# -d --build" da solo, su una macchina che non ha mai eseguito nulla di questo repository,
# non può quindi bastare: serve prima generare .env, i Docker secrets e i certificati TLS.
#
# Uso: sh scripts/avvia-tutto.sh (dalla radice del repository, dopo un git clone — non
# uno ZIP scaricato, scripts/setup-credenziali.sh richiede una vera repository Git)
set -eu
cd "$(dirname "$0")/.."

chmod +x infra/tomcat/*.sh scripts/*.sh

echo "=== 1/3: credenziali (.env, Docker secrets) ==="
sh scripts/setup-credenziali.sh

echo
echo "=== 2/3: certificati TLS locali ==="
sh scripts/genera-certificati-tls.sh

echo
echo "=== 3/3: avvio dello stack pulito ==="
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