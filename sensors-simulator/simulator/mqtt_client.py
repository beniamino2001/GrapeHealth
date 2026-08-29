"""
Wrapper minimale su paho-mqtt: connessione TLS con credenziali da .env, 
QoS 1 di default e riconnessione automatica gestita da paho stesso.

N.B.: un solo processo simulatore mantiene un'unica
connessione MQTT condivisa da tutti i nodi di tutte le parcelle.
Se il processo cade, tutti i nodi risultano "offline"
nello stesso istante.
"""

import os
import logging
import time
from pathlib import Path

import paho.mqtt.client as mqtt

logger = logging.getLogger("grapehealth.mqtt")

# Stessi valori usati sia per il backoff del primo handshake (v. connect())
# sia per reconnect_delay_set() nel costruttore: un solo posto dove leggere
# "quanto aspetta il simulatore prima di riprovare a raggiungere il broker".
RITARDO_MIN_RICONNESSIONE_SECONDI = 1
RITARDO_MAX_RICONNESSIONE_SECONDI = 30

# Tentativi per il SOLO primo handshake TCP+MQTT: le disconnessioni che
# avvengono DOPO una connessione riuscita sono già gestite indefinitamente
# da reconnect_delay_set()/loop_start() più sotto, che non hanno un limite
# di tentativi. Un limite finito qui evita che un errore di configurazione
# permanente (host sbagliato, credenziali mai valide) faccia attendere il
# simulatore all'infinito senza mai segnalare nulla di visibile.
MAX_TENTATIVI_CONNESSIONE_INIZIALE = 10

# Ritardo fisso e breve (non backoff esponenziale, a differenza di connect()):
# publish() è chiamata decine di volte per tick nel ciclo principale del
# simulatore, un backoff crescente qui rallenterebbe l'intero ciclo di
# pubblicazione per un singolo topic in difficoltà.
MAX_TENTATIVI_PUBLISH = 3
RITARDO_TENTATIVO_PUBLISH_SECONDI = 0.5

# Stessa CA locale che firma i certificati di postgres/rabbitmq/tomcat (v.
# scripts/genera-certificati-tls.sh), risolta relativamente a questo file
# invece che da variabile d'ambiente: funziona sia da host (repo clonato)
# sia dentro il container Tomcat, dove sensors-simulator/ e certs/ sono
# montate con la stessa struttura relativa (v. CONFIG_PATH in
# scripts/init_nodi_db.py, stesso principio).
CA_CERT_PATH = Path(__file__).resolve().parent.parent.parent / "certs" / "ca.crt"


class GrapeHealthMqttClient:
    def __init__(self, client_id: str, status_topic_prefix: str = "grapehealth/status"):
        self._client_id = client_id
        self._status_topic = f"{status_topic_prefix}/{client_id}"

        # Se RABBITMQ_USER/RABBITMQ_PASS non sono nell'ambiente, interrompe
        # subito con un messaggio esplicativo invece di connettersi in
        # silenzio con una credenziale condivisa in chiaro — stesso principio
        # già applicato a POSTGRES_USER/POSTGRES_PASSWORD in init_nodi_db.py.
        try:
            utente = os.environ["RABBITMQ_USER"]
            password = os.environ["RABBITMQ_PASS"]
        except KeyError as exc:
            raise RuntimeError(
                f"Variabile d'ambiente {exc} mancante: esegui ./scripts/setup-credentials.sh "
                "dalla root del repository e ricarica l'ambiente (.env) prima di rilanciare."
            ) from exc

        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
        )
        self.client.username_pw_set(utente, password)
        # Il listener MQTT in chiaro di RabbitMQ è disattivato (mqtt.listeners.tcp
        # = none in rabbitmq.conf): senza tls_set() qui, connect() fallirebbe
        # sempre, non solo in modo insicuro. ca_certs verifica il certificato del
        # broker contro la stessa CA locale che l'ha firmato — non una cifratura
        # "e basta" che accetterebbe qualunque certificato presentato.
        self.client.tls_set(ca_certs=str(CA_CERT_PATH))
        self.client.will_set(self._status_topic, payload="offline", qos=1, retain=True)
        self.client.reconnect_delay_set(
            min_delay=RITARDO_MIN_RICONNESSIONE_SECONDI,
            max_delay=RITARDO_MAX_RICONNESSIONE_SECONDI,
        )

        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

    def connect(
        self,
        max_tentativi: int = MAX_TENTATIVI_CONNESSIONE_INIZIALE,
        ritardo_iniziale_secondi: float = RITARDO_MIN_RICONNESSIONE_SECONDI,
        ritardo_massimo_secondi: float = RITARDO_MAX_RICONNESSIONE_SECONDI,
    ) -> None:
        """Se il broker non è ancora raggiungibile al primo tentativo (es. il
        simulatore parte prima che RabbitMQ abbia finito l'avvio), riprova
        con backoff esponenziale invece di terminare subito con un traceback
        non gestito. Il ritardo raddoppia a ogni tentativo fino al tetto
        ritardo_massimo_secondi. Esaurito max_tentativi, propaga l'ultima
        eccezione: un errore permanente (host sbagliato, credenziali mai
        valide) deve restare visibile, non trasformarsi in un'attesa
        infinita silenziosa."""
        if max_tentativi < 1:
            raise ValueError(f"max_tentativi deve essere almeno 1, ricevuto {max_tentativi}.")
        host = os.environ.get("MQTT_HOST", "localhost")
        port = int(os.environ.get("MQTT_PORT", 8883))
        ritardo = ritardo_iniziale_secondi
        for tentativo in range(1, max_tentativi + 1):
            try:
                self.client.connect(host, port, keepalive=30)
                break
            except OSError as exc:
                if tentativo == max_tentativi:
                    logger.error(
                        "Connessione al broker MQTT (%s:%s) fallita dopo %d tentativi: %s",
                        host, port, max_tentativi, exc,
                    )
                    raise
                logger.warning(
                    "Tentativo %d/%d di connessione al broker MQTT (%s:%s) fallito (%s), "
                    "nuovo tentativo fra %.1fs",
                    tentativo, max_tentativi, host, port, exc, ritardo,
                )
                time.sleep(ritardo)
                ritardo = min(ritardo * 2, ritardo_massimo_secondi)
        self.client.loop_start()
        self.client.publish(self._status_topic, "online", qos=1, retain=True)

    def publish(self, topic: str, payload: str, qos: int = 1) -> None:
        """Pubblica sempre con retain=True: un client MQTT nativo che si
        collega dopo l'ultima pubblicazione su un topic (es. MQTT Explorer
        durante un debug, o un futuro consumatore MQTT diretto) riceve
        subito l'ultimo valore noto, invece di aspettare fino al prossimo
        ciclo di pubblicazione. Non cambia nulla per i consumatori AMQP
        attuali (decisionengine, persistence): leggono da una coda già
        collegata all'exchange tramite binding permanente, non da una
        subscribe MQTT nativa — il meccanismo di retain di RabbitMQ
        riguarda solo quest'ultima.

        Riprova fino a MAX_TENTATIVI_PUBLISH volte su un fallimento locale
        (es. coda di invio piena, client non ancora connesso): copre i casi
        transitori più comuni senza bisogno di un buffer persistente su
        disco. Se anche l'ultimo tentativo fallisce, la lettura è comunque
        persa (nessuna coda di spool), ma il log passa da WARNING a ERROR
        proprio in quel momento, così una perdita di dato reale è sempre
        visibile e mai indistinguibile da un singolo blip transitorio."""
        for tentativo in range(1, MAX_TENTATIVI_PUBLISH + 1):
            result = self.client.publish(topic, payload, qos=qos, retain=True)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                return
            if tentativo < MAX_TENTATIVI_PUBLISH:
                logger.warning(
                    "Publish fallita su %s (rc=%s), tentativo %d/%d, nuovo tentativo fra %.1fs",
                    topic, result.rc, tentativo, MAX_TENTATIVI_PUBLISH, RITARDO_TENTATIVO_PUBLISH_SECONDI,
                )
                time.sleep(RITARDO_TENTATIVO_PUBLISH_SECONDI)
        logger.error(
            "Publish fallita su %s dopo %d tentativi (ultimo rc=%s): lettura persa.",
            topic, MAX_TENTATIVI_PUBLISH, result.rc,
        )

    def disconnect(self) -> None:
        self.client.publish(self._status_topic, "offline", qos=1, retain=True)
        self.client.loop_stop()
        self.client.disconnect()

    def _on_connect(self, client, userdata, connect_flags, reason_code, properties):
        if not reason_code.is_failure:
            logger.info("Connesso al broker MQTT (client_id=%s)", self._client_id)
        else:
            logger.error("Connessione MQTT fallita: %s", reason_code)

    def _on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        if reason_code.is_failure:
            logger.warning("Disconnessione inattesa dal broker (%s), riconnessione in corso...", reason_code)