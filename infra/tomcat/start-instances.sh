#!/usr/bin/env bash
set -e

TUTTE_LE_ISTANZE="decisionengine attuatori persistence api dashboard"
FILE_ESCLUSIONI="${ISTANZE_ESCLUSE_FILE:-/opt/grapehealth/istanze-escluse.conf}"

declare -A PORTA_HTTPS=(    [decisionengine]=8081 [attuatori]=8082 [persistence]=8083 [api]=8084 [dashboard]=8085 )
declare -A PORTA_SHUTDOWN=( [decisionengine]=8005 [attuatori]=8006 [persistence]=8007 [api]=8008 [dashboard]=8009 )
declare -A MODULO_SORGENTE=( [decisionengine]=backend [attuatori]=attuatori [persistence]=persistence [api]=api )
# dashboard non ha una voce in MODULO_SORGENTE: e' servita come file statici, non un modulo Maven.

PID_LIST=()
NOMI_AVVIATI=()
SENTINEL_CONTAINER="/opt/grapehealth/.container_gia_avviato"

# "Container ricreato da zero" (SENTINEL_CONTAINER assente: vive nel filesystem scrivibile
# del container, quindi sparisce a ogni "docker compose down" indipendentemente da "-v",
# a differenza di GRAPEHEALTH_INSTANCES che e' un bind mount e sopravvive a "-v" da solo) e'
# lo stesso segnale che pilota sia la pulizia dei log sia -- da qui in poi -- la ricompilazione
# dei quattro moduli Spring Boot: un riavvio qualunque non ricompila nulla, un container
# davvero nuovo ricompila sempre, anche se GRAPEHEALTH_INSTANCES contiene gia' le istanze
# di un avvio precedente.
if [[ ! -f "$SENTINEL_CONTAINER" ]]; then
  echo "Container ricreato da zero: pulizia dei log delle app Spring Boot..."
  find /opt/grapehealth/logs -maxdepth 1 -type f -name "grapehealth-*" -delete 2>/dev/null || true

  echo "Compilazione dei quattro moduli Spring Boot (mvn package)..."
  mkdir -p /opt/grapehealth/dist
  for istanza in decisionengine attuatori persistence api; do
    modulo="${MODULO_SORGENTE[$istanza]}"
    echo "  - $modulo: mvn package..."
    # timeout, non solo "mvn": una build che si blocca per un problema di rete durante
    # la risoluzione delle dipendenze (scenario reale ora che compila a ogni container
    # pulito, non più una tantum in fase di build immagine) lascerebbe altrimenti lo
    # script fermo qui a tempo indeterminato, senza che "docker compose up" mostri mai
    # un errore chiaro — solo un avvio che non finisce mai. 600s è ampio anche per un primo
    # download completo delle dipendenze (osservato ~40s in condizioni normali).
    ( cd "/opt/grapehealth/src/${modulo}" && timeout 600 mvn -q -DskipTests package )
    cp "/opt/grapehealth/src/${modulo}/target/"*.war "/opt/grapehealth/dist/${istanza}.war"
  done

  RICREA_ISTANZE=1
  touch "$SENTINEL_CONTAINER"
else
  echo "Container riavviato, non ricreato: log, istanze e WAR gia' compilati mantenuti."
  RICREA_ISTANZE=0
fi

provisiona_istanza() {
  local istanza="$1"
  local base="${GRAPEHEALTH_INSTANCES}/${istanza}"

  if [[ -f "${base}/conf/server.xml.template" && "$RICREA_ISTANZE" -eq 0 ]]; then
    return 0
  fi

  echo "  - $istanza: (ri)genero l'istanza in ${base}..."
  rm -rf "${base}"
  mkdir -p "${base}/conf" "${base}/logs" "${base}/temp" "${base}/webapps" "${base}/work" "${base}/bin"

  cp "${CATALINA_HOME}"/conf/* "${base}/conf/"

  sed -e "s/__PORTA_HTTPS__/${PORTA_HTTPS[$istanza]}/g" \
      -e "s/__PORTA_SHUTDOWN__/${PORTA_SHUTDOWN[$istanza]}/g" \
      /opt/grapehealth/templates/server.xml.template > "${base}/conf/server.xml.template"

  sed "s|__ENV_FILE_PATH__|${GIT_PATH:-/opt/grapehealth}/.env|g" \
      /opt/grapehealth/templates/setenv.sh.template > "${base}/bin/setenv.sh"
  chmod +x "${base}/bin/setenv.sh"

  if [[ "$istanza" == "dashboard" ]]; then
    mkdir -p "${base}/webapps/ROOT"
    cp -r /opt/grapehealth/dist/dashboard-src/. "${base}/webapps/ROOT/"
  else
    cp "/opt/grapehealth/dist/${istanza}.war" "${base}/webapps/ROOT.war"
  fi
}

echo "Sincronizzazione anagrafica nodi (init_nodi_db.py)..."
python3 /opt/grapehealth/sensors-simulator/scripts/init_nodi_db.py

leggi_escluse() {
  local escluse=()
  if [[ -f "$FILE_ESCLUSIONI" ]]; then
    while IFS= read -r riga || [[ -n "$riga" ]]; do
      riga="$(echo "$riga" | sed 's/#.*//' | xargs)"
      [[ -z "$riga" ]] && continue
      escluse+=("$riga")
    done < "$FILE_ESCLUSIONI"
  fi
  echo "${escluse[@]}"
}

contiene() {
  local elemento="$1"; shift
  for x in "$@"; do [[ "$x" == "$elemento" ]] && return 0; done
  return 1
}

ESCLUSE=($(leggi_escluse))

for nome in "${ESCLUSE[@]}"; do
  if ! contiene "$nome" $TUTTE_LE_ISTANZE; then
    echo "ERRORE: '$nome' in $FILE_ESCLUSIONI non e' un'istanza valida." >&2
    echo "Valori ammessi: $TUTTE_LE_ISTANZE" >&2
    exit 1
  fi
done

termina() {
  echo "Arresto delle istanze in corso..."
  for pid in "${PID_LIST[@]}"; do kill -TERM "$pid" 2>/dev/null || true; done
  wait
  exit 0
}
trap termina SIGTERM SIGINT

echo "Istanze escluse da questo avvio: ${ESCLUSE[*]:-nessuna}"

for istanza in $TUTTE_LE_ISTANZE; do
  if contiene "$istanza" "${ESCLUSE[@]}"; then
    echo "  - $istanza: SALTATA (esclusa da $FILE_ESCLUSIONI)"
    continue
  fi
  provisiona_istanza "$istanza"

  export CATALINA_BASE="${GRAPEHEALTH_INSTANCES}/${istanza}"
  export LOGGING_FILE_NAME="/opt/grapehealth/logs/grapehealth-${istanza}.log"

  SERVER_XML_TEMPLATE="${CATALINA_BASE}/conf/server.xml.template"
  if [[ -f "$SERVER_XML_TEMPLATE" ]]; then
    sed "s/__TLS_KEYSTORE_PASSWORD__/${TLS_KEYSTORE_PASSWORD}/g" "$SERVER_XML_TEMPLATE" > "${CATALINA_BASE}/conf/server.xml"
  fi

  echo "  - $istanza: avvio (CATALINA_BASE=${CATALINA_BASE})"
  ( cd "$CATALINA_BASE" && "${CATALINA_HOME}/bin/catalina.sh" run ) &
  PID_LIST+=($!)
  NOMI_AVVIATI+=("$istanza")
done

if [[ ${#NOMI_AVVIATI[@]} -eq 0 ]]; then
  echo "ERRORE: tutte e cinque le istanze sono escluse, nulla da avviare." >&2
  exit 1
fi

echo "Istanze avviate: ${NOMI_AVVIATI[*]}"
wait