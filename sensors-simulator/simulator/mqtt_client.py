"""
Wrapper minimale su paho-mqtt: connessione con credenziali da .env, 
QoS 1 di default e riconnessione automatica gestita da paho stesso.

N.B.: un solo processo simulatore mantiene un'unica
connessione MQTT condivisa da tutti i nodi di tutte le parcelle.
Se il processo cade, tutti i nodi risultano "offline"
nello stesso istante.
"""

import os
import logging

import paho.mqtt.client as mqtt

logger = logging.getLogger("grapehealth.mqtt")


class GrapeHealthMqttClient:
    def __init__(self, client_id: str, status_topic_prefix: str = "grapehealth/status"):
        self._client_id = client_id
        self._status_topic = f"{status_topic_prefix}/{client_id}"

        self.client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=client_id,
            protocol=mqtt.MQTTv311,
        )
        self.client.username_pw_set(
            os.environ.get("RABBITMQ_USER", "grapehealth"),
            os.environ.get("RABBITMQ_PASS", "grapehealth"),
        )
        self.client.will_set(self._status_topic, payload="offline", qos=1, retain=True)
        self.client.reconnect_delay_set(min_delay=1, max_delay=30)

        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

    def connect(self) -> None:
        host = os.environ.get("MQTT_HOST", "localhost")
        port = int(os.environ.get("MQTT_PORT", 1883))
        self.client.connect(host, port, keepalive=30)
        self.client.loop_start()
        self.client.publish(self._status_topic, "online", qos=1, retain=True)

    def publish(self, topic: str, payload: str, qos: int = 1) -> None:
        result = self.client.publish(topic, payload, qos=qos)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            logger.warning("Publish fallita su %s (rc=%s)", topic, result.rc)

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