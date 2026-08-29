#!/usr/bin/env bash
set -e

TUTTE_LE_ISTANZE="decisionengine attuatori persistence api dashboard"
FILE_ESCLUSIONI="${ISTANZE_ESCLUSE_FILE:-/opt/grapehealth/istanze-escluse.conf}"
PID_LIST=()
NOMI_AVVIATI=()
SENTINEL_CONTAINER="/opt/grapehealth/.container_gia_avviato"

if [[ ! -f "$SENTINEL_CONTAINER" ]]; then
  echo "Container ricreato da zero: pulizia dei log di Tomcat e delle app Spring Boot..."
  find "$GRAPEHEALTH_INSTANCES" -path "*/logs/*" -type f -delete 2>/dev/null || true
  find /opt/grapehealth/logs -maxdepth 1 -type f -name "grapehealth-*" -delete 2>/dev/null || true
  touch "$SENTINEL_CONTAINER"
else
  echo "Container riavviato, non ricreato: log esistenti mantenuti."
fi

echo "Sincronizzazione anagrafica nodi (init_nodi_db.py)..."
python3 /opt/grapehealth/sensors-simulator/scripts/init_nodi_db.py

# Legge il file di esclusione: una riga per istanza da NON avviare, righe vuote e
# commenti (#) ignorati. Il file può non esistere affatto (nessuna esclusione).
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

# Un nome nel file che non corrisponde a nessuna delle cinque istanze note è quasi
# certamente un errore di battitura — meglio fermarsi con un messaggio chiaro che
# avviare silenziosamente tutto come se il file fosse vuoto.
for nome in "${ESCLUSE[@]}"; do
  if ! contiene "$nome" $TUTTE_LE_ISTANZE; then
    echo "ERRORE: '$nome' in $FILE_ESCLUSIONI non è un'istanza valida." >&2
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