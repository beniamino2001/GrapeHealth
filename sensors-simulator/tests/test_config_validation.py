"""
Test di regressione su valida_config() in simulator/main.py.

File separato da test_generator.py perché valida_config() vive in
simulator/main.py, non in simulator/generator.py.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

import copy

import pytest

from simulator.main import (
    COLORI_BACCA_VALIDI,
    SCENARI_VALIDI,
    TIPI_NODO_VALIDI,
    TOPIC_PREFIX_ATTESO,
    carica_config,
    valida_config,
)


@pytest.fixture
def config():
    """La config reale del progetto (config/nodi.yaml): valida_config() deve accettare esattamente 
    quello che main.py userebbe davvero, non una versione semplificata costruita ad hoc per il test."""
    return carica_config()


class TestConfigValidaNonSollevaEccezioni:
    def test_config_reale_passa_la_validazione(self, config):
        # Non deve sollevare nulla: se questo fallisce, valida_config() è
        # troppo restrittiva anche per la configurazione reale del progetto.
        valida_config(config, "ondata_di_calore")


class TestScenarioNonValido:
    @pytest.mark.parametrize("scenario_typo", [
        "ondata di calore",   # spazio invece di underscore
        "Normale",             # maiuscola
        "stress idrico",
        "",
        "caldo",
    ])
    def test_scenario_con_typo_solleva_errore(self, config, scenario_typo):
        with pytest.raises(ValueError, match="Scenario non valido"):
            valida_config(config, scenario_typo)

    @pytest.mark.parametrize("scenario_valido", SCENARI_VALIDI)
    def test_ciascuno_scenario_valido_passa(self, config, scenario_valido):
        valida_config(config, scenario_valido)


class TestColoreBaccaNonValido:
    @pytest.mark.parametrize("colore_typo", ["Nero", "NERO", "bianca", "rosso", "", None])
    def test_colore_bacca_con_typo_solleva_errore(self, config, colore_typo):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["colore_bacca"] = colore_typo
        with pytest.raises(ValueError, match="colore_bacca non valido"):
            valida_config(config_bad, "normale")

    @pytest.mark.parametrize("colore_valido", COLORI_BACCA_VALIDI)
    def test_ciascun_colore_valido_passa(self, config, colore_valido):
        config_ok = copy.deepcopy(config)
        config_ok["parcelle"][0]["colore_bacca"] = colore_valido
        valida_config(config_ok, "normale")


class TestTipoNodoNonValido:
    @pytest.mark.parametrize("tipo_typo", ["Meteo", "meteoo", "suolo", ""])
    def test_tipo_nodo_con_typo_solleva_errore(self, config, tipo_typo):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["nodi"][0]["tipo"] = tipo_typo
        with pytest.raises(ValueError, match="tipo nodo non valido"):
            valida_config(config_bad, "normale")

    @pytest.mark.parametrize("tipo_valido", TIPI_NODO_VALIDI)
    def test_ciascun_tipo_valido_passa(self, config, tipo_valido):
        config_ok = copy.deepcopy(config)
        config_ok["parcelle"][0]["nodi"][0]["tipo"] = tipo_valido
        valida_config(config_ok, "normale")


class TestTopicPrefixNonValido:
    """A differenza degli altri tre campi, topic_prefix non ha un insieme di
    alternative valide: ha un solo valore corretto, vincolato dalla routing
    key hardcoded lato Java."""

    @pytest.mark.parametrize("prefix_typo", [
        "Grapehealth", "grape_health", "grapehealth2", "", None,
    ])
    def test_topic_prefix_diverso_dall_atteso_solleva_errore(self, config, prefix_typo):
        config_bad = copy.deepcopy(config)
        config_bad["mqtt"]["topic_prefix"] = prefix_typo
        with pytest.raises(ValueError, match="mqtt.topic_prefix non valido"):
            valida_config(config_bad, "normale")

    def test_topic_prefix_atteso_passa(self, config):
        config_ok = copy.deepcopy(config)
        config_ok["mqtt"]["topic_prefix"] = TOPIC_PREFIX_ATTESO
        valida_config(config_ok, "normale")

    def test_messaggio_nomina_i_file_java_da_aggiornare_insieme(self, config):
        config_bad = copy.deepcopy(config)
        config_bad["mqtt"]["topic_prefix"] = "altro"
        with pytest.raises(ValueError, match="RabbitConfig"):
            valida_config(config_bad, "normale")


class TestMessaggioErroreIdentificaIlCampo:
    """Un ValueError generico ('config non valida') costringerebbe a rileggere
    l'intero file per capire cosa correggere: il messaggio deve nominare
    esplicitamente il campo e il valore trovato."""

    def test_messaggio_scenario_contiene_il_valore_sbagliato(self, config):
        with pytest.raises(ValueError, match="ondata di calore"):
            valida_config(config, "ondata di calore")

    def test_messaggio_colore_bacca_nomina_la_parcella(self, config):
        config_bad = copy.deepcopy(config)
        nome_parcella = config_bad["parcelle"][0]["nome"]
        config_bad["parcelle"][0]["colore_bacca"] = "Nero"
        with pytest.raises(ValueError, match=nome_parcella):
            valida_config(config_bad, "normale")

    def test_messaggio_tipo_nodo_nomina_il_codice_nodo(self, config):
        config_bad = copy.deepcopy(config)
        codice_nodo = config_bad["parcelle"][0]["nodi"][0]["codice"]
        config_bad["parcelle"][0]["nodi"][0]["tipo"] = "meteoo"
        with pytest.raises(ValueError, match=codice_nodo):
            valida_config(config_bad, "normale")