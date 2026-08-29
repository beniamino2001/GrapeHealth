#!/usr/bin/env sh
set -eu

# Genera una CA locale autofirmata e un certificato server per ciascun servizio
# dell'infrastruttura (postgres, rabbitmq, tomcat) — pensata per un ambiente di
# sviluppo locale che vuole comunque parlare TLS come se fosse in produzione, non
# per un certificato pubblicamente attendibile (nessuna CA reale firmerebbe un nome
# come "postgres" o "rabbitmq", validi solo dentro la rete Docker di questo progetto).
# Rilanciabile: se i certificati esistono già, non li tocca — stessa idempotenza di
# setup-credentials.sh, per lo stesso motivo (rigenerarli invaliderebbe le connessioni
# già stabilite dai client con l'impronta del certificato precedente).
#
# Scritto in POSIX sh puro (nessun [[ ]], nessuna sostituzione di processo <(...),
# nessun array): questo repository lancia i propri script con "sh nomefile.sh", che
# su macOS e Linux ignora lo shebang ed esegue con una shell POSIX rigida (dash),
# non bash — una sintassi bash-specifica qui fallirebbe silenziosamente in un modo
# che sembra un bug dello script, non una scelta di shell.
#
# I certificati foglia (postgres/rabbitmq/tomcat) includono esplicitamente
# basicConstraints, keyUsage e extendedKeyUsage=serverAuth, e hanno validità
# limitata a 825 giorni: sono i requisiti minimi imposti da Apple (macOS/Safari/
# WebKit) dal 2019 per considerare un certificato TLS server "conforme agli
# standard" — senza questi, il certificato viene importato nel keychain senza
# errori ma viene comunque rifiutato in fase di handshake su qualunque host Apple.
#
# La CA locale viene inoltre importata automaticamente nel trust store di sistema
# (importa_ca_locale, sotto): basta fidarsi della CA, non dei singoli certificati
# foglia, perché la catena di fiducia scende automaticamente da essa. Il rilevamento
# del sistema operativo è best-effort: se non riconosciuto, lo script stampa il
# comando manuale invece di fallire, per non bloccare la generazione dei certificati
# (che è l'obiettivo primario dello script).

CERTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/certs"
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
GIORNI_VALIDITA_CA=3650
GIORNI_VALIDITA_LEAF=825

importa_ca_locale() {
  ca_path="$CERTS_DIR/ca.crt"
  cn="GrapeHealth Local Dev CA"
  os="$(uname -s 2>/dev/null || echo sconosciuto)"

  case "$os" in
    Darwin)
      echo "Sistema rilevato: macOS — import della CA nel System keychain (richiede sudo)..."
      if ! command -v security >/dev/null 2>&1; then
        echo "ATTENZIONE: comando 'security' non trovato, import CA saltato." >&2
        return 0
      fi
      existing_hashes="$(sudo security find-certificate -a -c "$cn" -Z /Library/Keychains/System.keychain 2>/dev/null | awk '/SHA-1 hash/{print $NF}' || true)"
      if [ -n "$existing_hashes" ]; then
        echo "$existing_hashes" | while read -r hash; do
          sudo security delete-certificate -Z "$hash" /Library/Keychains/System.keychain 2>/dev/null || true
        done
      fi
      if sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "$ca_path" 2>/dev/null; then
        echo "CA importata e marcata trusted nel System keychain."
      else
        echo "ATTENZIONE: import CA nel keychain fallito. Importala manualmente con:" >&2
        echo "  sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $ca_path" >&2
      fi
      ;;

    Linux)
      if command -v update-ca-certificates >/dev/null 2>&1 && [ -d /usr/local/share/ca-certificates ]; then
        echo "Sistema rilevato: Linux (Debian/Ubuntu) — import in /usr/local/share/ca-certificates..."
        if sudo cp "$ca_path" /usr/local/share/ca-certificates/grapehealth-local-dev-ca.crt 2>/dev/null \
          && sudo update-ca-certificates >/dev/null 2>&1; then
          echo "CA importata nel trust store di sistema."
        else
          echo "ATTENZIONE: import CA fallito. Importala manualmente con:" >&2
          echo "  sudo cp $ca_path /usr/local/share/ca-certificates/grapehealth-local-dev-ca.crt && sudo update-ca-certificates" >&2
        fi
      elif command -v update-ca-trust >/dev/null 2>&1 && [ -d /etc/pki/ca-trust/source/anchors ]; then
        echo "Sistema rilevato: Linux (RHEL/Fedora/CentOS) — import in /etc/pki/ca-trust..."
        if sudo cp "$ca_path" /etc/pki/ca-trust/source/anchors/grapehealth-local-dev-ca.crt 2>/dev/null \
          && sudo update-ca-trust extract 2>/dev/null; then
          echo "CA importata nel trust store di sistema."
        else
          echo "ATTENZIONE: import CA fallito. Importala manualmente con:" >&2
          echo "  sudo cp $ca_path /etc/pki/ca-trust/source/anchors/grapehealth-local-dev-ca.crt && sudo update-ca-trust extract" >&2
        fi
      else
        echo "ATTENZIONE: distribuzione Linux non riconosciuta (né update-ca-certificates né update-ca-trust trovati)." >&2
        echo "Importa manualmente $ca_path nel trust store della tua distro." >&2
      fi
      if grep -qi microsoft /proc/version 2>/dev/null; then
        echo "NOTA: sembra WSL — l'import sopra vale solo per processi Linux dentro WSL." >&2
        echo "Se apri l'applicazione da un browser Windows, importa $ca_path anche lì con:" >&2
        echo "  certutil.exe -addstore -f ROOT \"\$(wslpath -w $ca_path)\"" >&2
      fi
      ;;

    MINGW*|MSYS*|CYGWIN*)
      echo "Sistema rilevato: Windows (Git Bash/MSYS/Cygwin) — import con certutil..."
      if command -v certutil.exe >/dev/null 2>&1; then
        ca_path_win="$(cygpath -w "$ca_path" 2>/dev/null || echo "$ca_path")"
        if certutil.exe -addstore -f "ROOT" "$ca_path_win" >/dev/null 2>&1; then
          echo "CA importata nello store 'ROOT' di Windows."
        else
          echo "ATTENZIONE: import CA fallito (serve prompt UAC/amministratore)." >&2
          echo "Importala manualmente aprendo $ca_path e cliccando 'Installa certificato' -> 'Autorità di certificazione radice attendibili'." >&2
        fi
      else
        echo "ATTENZIONE: certutil.exe non trovato nel PATH. Importa manualmente $ca_path via certmgr.msc." >&2
      fi
      ;;

    *)
      echo "ATTENZIONE: sistema operativo '$os' non riconosciuto — import automatico CA saltato." >&2
      echo "Importa manualmente $ca_path come CA attendibile nel tuo sistema/browser." >&2
      ;;
  esac

  echo "NOTA: Firefox usa un proprio archivio certificati (NSS), indipendente da" >&2
  echo "quello di sistema — se l'applicazione dà ancora errore certificato solo su" >&2
  echo "Firefox, importa $ca_path manualmente in about:preferences#privacy -> Certificati." >&2
}

if [ ! -f "$ENV_FILE" ]; then
  echo "ERRORE: $ENV_FILE non trovato. Esegui prima ./scripts/setup-credentials.sh," >&2
  echo "che genera anche la password del keystore Java letta da questo script." >&2
  exit 1
fi
TLS_KEYSTORE_PASSWORD="$(grep '^TLS_KEYSTORE_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$TLS_KEYSTORE_PASSWORD" ]; then
  echo "ERRORE: TLS_KEYSTORE_PASSWORD non trovata in $ENV_FILE." >&2
  echo "Rigenera .env con ./scripts/setup-credentials.sh prima di rilanciare questo script." >&2
  exit 1
fi

if [ -f "$CERTS_DIR/ca.crt" ]; then
  echo "Certificati già presenti in $CERTS_DIR — nessuna rigenerazione."
  echo "Per rigenerarli da zero: rm -rf $CERTS_DIR, poi rilancia questo script."
  echo "Verifico comunque che la CA sia importata nel trust store locale..."
  importa_ca_locale
  exit 0
fi

mkdir -p "$CERTS_DIR"

echo "Genero la CA locale..."
ca_ext_file="$CERTS_DIR/ca.ext.tmp"
cat > "$ca_ext_file" <<EOF
basicConstraints=critical,CA:TRUE
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
EOF
openssl req -x509 -newkey rsa:4096 -sha256 -days "$GIORNI_VALIDITA_CA" -nodes \
  -keyout "$CERTS_DIR/ca.key" -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=GrapeHealth Local Dev CA" \
  -extensions v3_ca -config "$ca_ext_file" 2>/dev/null || \
openssl req -x509 -newkey rsa:4096 -sha256 -days "$GIORNI_VALIDITA_CA" -nodes \
  -keyout "$CERTS_DIR/ca.key" -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=GrapeHealth Local Dev CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
rm -f "$ca_ext_file"

genera_certificato_servizio() {
  nome="$1"
  sans="$2"

  openssl req -newkey rsa:3072 -sha256 -nodes \
    -keyout "$CERTS_DIR/${nome}.key" -out "$CERTS_DIR/${nome}.csr" \
    -subj "/CN=${nome}" 2>/dev/null

  ext_file="$CERTS_DIR/${nome}.ext.tmp"
  cat > "$ext_file" <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=${sans}
EOF

  openssl x509 -req -in "$CERTS_DIR/${nome}.csr" \
    -CA "$CERTS_DIR/ca.crt" -CAkey "$CERTS_DIR/ca.key" -CAcreateserial \
    -days "$GIORNI_VALIDITA_LEAF" -sha256 \
    -extfile "$ext_file" \
    -out "$CERTS_DIR/${nome}.crt" 2>/dev/null

  rm -f "$CERTS_DIR/${nome}.csr" "$ext_file"
}

echo "Genero il certificato per postgres..."
genera_certificato_servizio postgres "DNS:postgres,DNS:localhost,IP:127.0.0.1"
echo "Genero il certificato per rabbitmq..."
genera_certificato_servizio rabbitmq "DNS:rabbitmq,DNS:localhost,IP:127.0.0.1"
echo "Genero il certificato per tomcat (condiviso dalle cinque istanze, un solo hostname)..."
genera_certificato_servizio tomcat "DNS:tomcat,DNS:localhost,IP:127.0.0.1"

openssl pkcs12 -export -in "$CERTS_DIR/tomcat.crt" -inkey "$CERTS_DIR/tomcat.key" \
  -out "$CERTS_DIR/tomcat.p12" -name tomcat -passout "pass:${TLS_KEYSTORE_PASSWORD}"
echo "Genero il truststore Java con la CA locale..."
keytool -importcert -noprompt -alias grapehealth-ca \
  -file "$CERTS_DIR/ca.crt" -keystore "$CERTS_DIR/truststore.p12" \
  -storetype PKCS12 -storepass "$TLS_KEYSTORE_PASSWORD" >/dev/null 2>&1

chmod 600 "$CERTS_DIR"/*.key "$CERTS_DIR/tomcat.p12" "$CERTS_DIR/truststore.p12"
chmod 644 "$CERTS_DIR"/*.crt

echo "Importo la CA locale nel trust store del sistema operativo..."
importa_ca_locale

echo
echo "Certificati generati in $CERTS_DIR:"
ls -la "$CERTS_DIR"