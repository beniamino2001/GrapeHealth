#!/usr/bin/env bash
# Genera credenziali locali per GrapeHealth (PostgreSQL + RabbitMQ), una sola volta, senza mai scriverle in chiaro.
#
# Uso:
#   chmod +x scripts/setup-credentials.sh (solo la prima volta)
#   ./scripts/setup-credentials.sh
#
# Cosa fa:
#   1. genera due password casuali forti (openssl rand);
#   2. scrive secrets/postgres_user.txt e secrets/postgres_password.txt,
#      letti dal container Postgres tramite Docker secrets (POSTGRES_*_FILE);
#   3. genera infra/rabbitmq/definitions.json con l'utente amministrativo di RabbitMQ
#      e la password già hashata (rabbitmqctl hash_password, non una reimplementazione
#      artigianale dell'algoritmo), caricato al boot da management.load_definitions;
#   4. copia .env.example in .env sostituendo i placeholder con le credenziali generate,
#      letto dalle app Spring Boot locali deployate sulle relative istanze Tomcat tramite 
#      setenv.sh per autenticarsi come client AMQP/JDBC — le password restano comunque 
#      necessarie in chiaro lì, perché un client deve sempre presentare la credenziale reale per autenticarsi.
set -euo pipefail
cd "$(dirname "$0")/.."

SECRETS_DIR="secrets"
ENV_FILE=".env"
ENV_TEMPLATE=".env.example"
RABBITMQ_IMAGE="rabbitmq:4.3-management"
RABBITMQ_DEFINITIONS="infra/rabbitmq/definitions.json"
RABBITMQ_DEFINITIONS_TEMPLATE="infra/rabbitmq/definitions.json.example"

# Se anche uno solo degli artefatti generati esiste già, evita di rigenerare gli altri con valori nuovi e disallineati
# rispetto a un volume Docker che potrebbe contenere ancora l'utente creato con le credenziali precedenti.
ARTEFATTI_ESISTENTI=()
[[ -f "$ENV_FILE" ]] && ARTEFATTI_ESISTENTI+=("$ENV_FILE")
[[ -f "$SECRETS_DIR/postgres_user.txt" ]] && ARTEFATTI_ESISTENTI+=("$SECRETS_DIR/postgres_user.txt")
[[ -f "$SECRETS_DIR/postgres_password.txt" ]] && ARTEFATTI_ESISTENTI+=("$SECRETS_DIR/postgres_password.txt")
[[ -f "$RABBITMQ_DEFINITIONS" ]] && ARTEFATTI_ESISTENTI+=("$RABBITMQ_DEFINITIONS")

if [[ ${#ARTEFATTI_ESISTENTI[@]} -gt 0 ]]; then
  echo "Trovati artefatti di credenziali già esistenti, nessuno viene toccato:"
  printf '  - %s\n' "${ARTEFATTI_ESISTENTI[@]}"
  echo
  echo "Per rigenerare TUTTE le credenziali da zero (consigliato: non rigenerarne solo alcune):"
  echo "  rm -f .env infra/rabbitmq/definitions.json"
  echo "  rm -rf secrets/"
  echo "  docker compose down -v   # essenziale: altrimenti i container mantengono l'utente"
  echo "                           # creato con le credenziali precedenti, disallineato da"
  echo "                           # quelle appena rigenerate"
  echo "  ./scripts/setup-credentials.sh"
  exit 0
fi

# Se RABBITMQ_USER/RABBITMQ_PASS o POSTGRES_USER/POSTGRES_PASSWORD sono già esportate nella shell di esecuzione, 
# continueranno a valere anche dopo aver generato un .env nuovo, finché non apri un terminale pulito o le fai unset qui sotto.
STANTIE=$(env | grep -E '^(RABBITMQ_USER|RABBITMQ_PASS|POSTGRES_USER|POSTGRES_PASSWORD)=' || true)
if [[ -n "$STANTIE" ]]; then
  echo "ATTENZIONE: in questa shell risultano già esportate delle variabili con questi nomi:"
  echo "$STANTIE" | sed 's/=.*/=[valore nascosto]/'
  echo "Se non le fai unset ora (o non apri un terminale nuovo), continueranno a valere"
  echo "al posto di quelle appena generate in .env quando lanci 'docker compose up'."
  echo
fi

if ! docker info > /dev/null 2>&1; then
  echo "Docker non risulta in esecuzione: avvia Docker Desktop e rilancia lo script" \
       "(serve per generare l'hash della password RabbitMQ con rabbitmqctl)." >&2
  exit 1
fi

mkdir -p "$SECRETS_DIR"

random_secret() {
  # 24 caratteri alfanumerici che non disturbano URL di connessione, file .env o il JSON delle definizioni RabbitMQ
  openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | cut -c1-24
}

PG_USER="grapehealth"
PG_PASS="$(random_secret)"
RMQ_USER="grapehealth"
RMQ_PASS="$(random_secret)"

# --- PostgreSQL: file letti via Docker secrets ---
printf '%s' "$PG_USER" > "$SECRETS_DIR/postgres_user.txt"
printf '%s' "$PG_PASS" > "$SECRETS_DIR/postgres_password.txt"
chmod 600 "$SECRETS_DIR"/postgres_user.txt "$SECRETS_DIR"/postgres_password.txt

# --- RabbitMQ: definitions.json generato dal template, con password già hashata dal tool ufficiale ---
echo "Genero l'hash della password RabbitMQ (rabbitmqctl hash_password, immagine ${RABBITMQ_IMAGE})..."
RMQ_HASH=$(docker run --rm "$RABBITMQ_IMAGE" rabbitmqctl hash_password "$RMQ_PASS" | tail -n1 | tr -d '\r')

cp "$RABBITMQ_DEFINITIONS_TEMPLATE" "$RABBITMQ_DEFINITIONS"
sed -i.bak "s|GENERATO_DA_setup-credentials.sh_NON_MODIFICARE_A_MANO|${RMQ_HASH}|" "$RABBITMQ_DEFINITIONS"
rm -f "${RABBITMQ_DEFINITIONS}.bak"
chmod 600 "$RABBITMQ_DEFINITIONS"

# --- .env: letto dalle app Spring Boot locali deployate sulle relative istanze Tomcat tramite setenv.sh ---
cp "$ENV_TEMPLATE" "$ENV_FILE"
sed -i.bak \
  -e "s/^POSTGRES_USER=.*/POSTGRES_USER=${PG_USER}/" \
  -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${PG_PASS}/" \
  -e "s/^RABBITMQ_USER=.*/RABBITMQ_USER=${RMQ_USER}/" \
  -e "s/^RABBITMQ_PASS=.*/RABBITMQ_PASS=${RMQ_PASS}/" \
  "$ENV_FILE"
rm -f "${ENV_FILE}.bak"
chmod 600 "$ENV_FILE"

cat <<MSG

Creati:
  - $SECRETS_DIR/postgres_user.txt, $SECRETS_DIR/postgres_password.txt   (letti dal container PostgreSQL via Docker secrets)
  - $RABBITMQ_DEFINITIONS                                                (letto dal container RabbitMQ al boot, password già hashata)
  - $ENV_FILE                                                            (letto dalle app Spring Boot locali via plugin EnvFile)

Credenziali generate (annotale se ti servono per un client esterno, es. MQTT Explorer):
  PostgreSQL -> utente: $PG_USER   password: $PG_PASS
  RabbitMQ   -> utente: $RMQ_USER   password: $RMQ_PASS

Nessuno di questi valori è stato scritto in chiaro in un file tracciato da Git
(.env, secrets/ e infra/rabbitmq/definitions.json sono in .gitignore).

Se in futuro vuoi rigenerare le credenziali, ricordati di lanciare anche
'docker compose down -v' prima di ripartire: altrimenti i container mantengono
l'utente creato con le credenziali precedenti, disallineato da quelle nuove.

Ora puoi lanciare: docker compose up -d
MSG
