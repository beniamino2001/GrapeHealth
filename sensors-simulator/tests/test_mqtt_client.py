"""
Test su simulator/mqtt_client.py: il wrapper su paho-mqtt.

Nessun broker reale coinvolto: paho.mqtt.client.Client viene sostituito con
un finto che registra le chiamate ricevute, così questi test restano
rapidi e non dipendono da un'infrastruttura esterna in esecuzione. La
verifica contro un broker vero resta compito delle run end-to-end
dell'intero stack, non di questi test.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

from unittest.mock import MagicMock

import pytest

import simulator.mqtt_client as mqtt_client_module
from simulator.mqtt_client import GrapeHealthMqttClient


class RisultatoPublishFinto:
    def __init__(self, rc):
        self.rc = rc


@pytest.fixture
def client_paho_finto(monkeypatch):
    """Sostituisce mqtt.Client con un MagicMock: ogni GrapeHealthMqttClient
    costruito durante il test userà questo finto al posto di una
    connessione di rete reale."""
    classe_finta = MagicMock()
    istanza_finta = classe_finta.return_value
    istanza_finta.publish.return_value = RisultatoPublishFinto(rc=0)  # MQTT_ERR_SUCCESS
    monkeypatch.setattr(mqtt_client_module.mqtt, "Client", classe_finta)
    return istanza_finta


@pytest.fixture(autouse=True)
def credenziali_rabbitmq_di_test(monkeypatch):
    """Imposta credenziali di test per ogni test di questo file, così un
    ambiente reale senza RABBITMQ_USER/RABBITMQ_PASS impostate (o con
    valori diversi da questi) non fa fallire i test che non riguardano
    esplicitamente le credenziali stesse — stesso principio già applicato
    all'isolamento da .env in test_init_nodi_db.py. TestCredenzialiMancanti
    disattiva questa fixture con monkeypatch.delenv() dove serve testare
    proprio l'assenza."""
    monkeypatch.setenv("RABBITMQ_USER", "utente_test")
    monkeypatch.setenv("RABBITMQ_PASS", "password_test")


class TestCostruzione:
    def test_credenziali_lette_dalle_variabili_ambiente(self, client_paho_finto, monkeypatch):
        monkeypatch.setenv("RABBITMQ_USER", "un_altro_utente")
        monkeypatch.setenv("RABBITMQ_PASS", "un_altra_password")

        GrapeHealthMqttClient(client_id="nodo-test")

        client_paho_finto.username_pw_set.assert_called_once_with("un_altro_utente", "un_altra_password")

    def test_topic_di_stato_include_prefix_e_client_id(self, client_paho_finto):
        GrapeHealthMqttClient(client_id="sensori-simulati", status_topic_prefix="grapehealth/status")

        topic_atteso = "grapehealth/status/sensori-simulati"
        args, kwargs = client_paho_finto.will_set.call_args
        assert args[0] == topic_atteso

    def test_lwt_configurato_con_payload_offline_e_retain(self, client_paho_finto):
        GrapeHealthMqttClient(client_id="sensori-simulati")

        _, kwargs = client_paho_finto.will_set.call_args
        assert kwargs.get("payload") == "offline"
        assert kwargs.get("retain") is True


class TestConnect:
    def test_connect_pubblica_online_dopo_la_connessione(self, client_paho_finto, monkeypatch):
        monkeypatch.setenv("MQTT_HOST", "broker.test")
        monkeypatch.setenv("MQTT_PORT", "1884")
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client.connect()

        client_paho_finto.connect.assert_called_once_with("broker.test", 1884, keepalive=30)
        client_paho_finto.loop_start.assert_called_once()
        topic_atteso = "grapehealth/status/sensori-simulati"
        client_paho_finto.publish.assert_any_call(topic_atteso, "online", qos=1, retain=True)


class TestPublish:
    def test_publish_riuscita_non_logga_alcun_warning(self, client_paho_finto, caplog):
        client_paho_finto.publish.return_value = RisultatoPublishFinto(rc=0)
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client.publish("grapehealth/parcellaA/meteo-A1/temperatura_aria", '{"valore": 28.5}')

        assert not any("fallita" in r.message for r in caplog.records)

    def test_publish_fallita_logga_un_warning_ma_non_solleva_eccezioni(self, client_paho_finto, caplog):
        client_paho_finto.publish.return_value = RisultatoPublishFinto(rc=4)  # un codice di errore qualsiasi
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client.publish("grapehealth/parcellaA/meteo-A1/temperatura_aria", '{"valore": 28.5}')

        assert any("fallita" in r.message for r in caplog.records), (
            "Una publish fallita a livello locale dovrebbe produrre un log di "
            "warning, altrimenti passerebbe inosservata."
        )

    def test_publish_passa_qos_richiesto_al_client_sottostante(self, client_paho_finto):
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client.publish("un/topic", "payload", qos=1)

        client_paho_finto.publish.assert_any_call("un/topic", "payload", qos=1)


class TestDisconnect:
    def test_disconnect_pubblica_offline_prima_di_chiudere_la_connessione(self, client_paho_finto):
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client.disconnect()

        topic_atteso = "grapehealth/status/sensori-simulati"
        client_paho_finto.publish.assert_any_call(topic_atteso, "offline", qos=1, retain=True)
        client_paho_finto.loop_stop.assert_called_once()
        client_paho_finto.disconnect.assert_called_once()


class RagioneCodiceFinta:
    """Sostituisce il ReasonCode di paho: espone solo l'attributo is_failure
    che _on_connect/_on_disconnect leggono davvero."""

    def __init__(self, is_failure: bool):
        self.is_failure = is_failure

    def __str__(self):
        return "fallita" if self.is_failure else "ok"


class TestCallbackConnessione:
    """_on_connect e _on_disconnect sono registrati su paho ma mai invocati
    da un broker reale nei test: si chiamano qui direttamente, come farebbe
    paho, con un ReasonCode finto."""

    def test_on_connect_logga_info_se_non_fallita(self, client_paho_finto, caplog):
        import logging
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        with caplog.at_level(logging.INFO):
            client._on_connect(client.client, None, {}, RagioneCodiceFinta(is_failure=False), None)

        assert any("Connesso" in r.message for r in caplog.records)
        assert not any(r.levelname == "ERROR" for r in caplog.records)

    def test_on_connect_logga_errore_se_fallita(self, client_paho_finto, caplog):
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client._on_connect(client.client, None, {}, RagioneCodiceFinta(is_failure=True), None)

        assert any(r.levelname == "ERROR" for r in caplog.records)

    def test_on_disconnect_logga_warning_se_inattesa(self, client_paho_finto, caplog):
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client._on_disconnect(client.client, None, {}, RagioneCodiceFinta(is_failure=True), None)

        assert any(r.levelname == "WARNING" for r in caplog.records)

    def test_on_disconnect_non_logga_warning_se_richiesta_volontariamente(self, client_paho_finto, caplog):
        client = GrapeHealthMqttClient(client_id="sensori-simulati")

        client._on_disconnect(client.client, None, {}, RagioneCodiceFinta(is_failure=False), None)

        assert not any(r.levelname == "WARNING" for r in caplog.records)


class TestCredenzialiMancanti:
    """Nessuna delle due variabili deve mai avere un default silenzioso: una
    credenziale condivisa in chiaro come fallback sarebbe esattamente il
    problema che questo controllo esiste per evitare."""

    def test_senza_rabbitmq_user_solleva_errore_e_non_costruisce_il_client(self, client_paho_finto, monkeypatch):
        monkeypatch.delenv("RABBITMQ_USER", raising=False)
        with pytest.raises(RuntimeError, match="RABBITMQ_USER"):
            GrapeHealthMqttClient(client_id="nodo-test")
        client_paho_finto.username_pw_set.assert_not_called()

    def test_senza_rabbitmq_pass_solleva_errore_e_non_costruisce_il_client(self, client_paho_finto, monkeypatch):
        monkeypatch.delenv("RABBITMQ_PASS", raising=False)
        with pytest.raises(RuntimeError, match="RABBITMQ_PASS"):
            GrapeHealthMqttClient(client_id="nodo-test")
        client_paho_finto.username_pw_set.assert_not_called()