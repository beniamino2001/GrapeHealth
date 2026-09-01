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
    valida_sezioni_config,
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
        valida_config(config, "ondata_di_calore", 1)

class TestValidaSezioniConfig:
    """valida_sezioni_config() gira in main() PRIMA di valida_config(), sulle
    due sezioni di primo livello che main() indicizza direttamente un
    istante dopo — un livello più in alto rispetto a tutto il resto di
    questo file, dove ogni altro controllo vive dentro valida_config()
    stessa."""

    def test_config_reale_passa(self, config):
        valida_sezioni_config(config)

    @pytest.mark.parametrize("sezione_mancante", ["simulazione", "mqtt"])
    @pytest.mark.parametrize("valore_non_valido", [None, "assente", "una stringa", 42])
    def test_sezione_mancante_o_non_un_dizionario_solleva_errore(
        self, config, sezione_mancante, valore_non_valido
    ):
        config_bad = copy.deepcopy(config)
        if valore_non_valido == "assente":
            del config_bad[sezione_mancante]
        else:
            config_bad[sezione_mancante] = valore_non_valido
        with pytest.raises(ValueError, match=sezione_mancante):
            valida_sezioni_config(config_bad)

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
            valida_config(config, scenario_typo, 1)

    @pytest.mark.parametrize("scenario_valido", SCENARI_VALIDI)
    def test_ciascuno_scenario_valido_passa(self, config, scenario_valido):
        valida_config(config, scenario_valido, 1)

class TestSezioneParcelleMancanteONonValida:
    """A differenza di ogni altro controllo di valida_config(), qui l'errore
    di partenza non sarebbe un valore sbagliato dentro 'parcelle', ma
    l'assenza stessa della chiave (o una lista vuota) — un caso limite più
    strutturale che di contenuto, ma con la stessa conseguenza pratica di
    un qualunque altro campo non valido: un KeyError grezzo invece di un
    messaggio che dica cosa manca."""

    @pytest.mark.parametrize("parcelle_non_valide", [None, [], "assente"])
    def test_parcelle_mancante_vuota_o_none_solleva_errore_esplicito(self, config, parcelle_non_valide):
        config_bad = copy.deepcopy(config)
        if parcelle_non_valide == "assente":
            del config_bad["parcelle"]
        else:
            config_bad["parcelle"] = parcelle_non_valide
        with pytest.raises(ValueError, match="parcelle"):
            valida_config(config_bad, "normale", 1)

class TestSezioneNodiMancanteInUnaParcella:
    """Prima di questa classe, valida_config() usava
    parcella.get('nodi', []): una parcella senza 'nodi' passava la
    validazione (zero nodi, nessun errore), ma il ciclo di pubblicazione in
    main() usa parcella['nodi'] senza .get() — la stessa config sarebbe
    quindi andata in crash con un KeyError al primo tick, dopo che
    valida_config() aveva già dichiarato tutto a posto. Stessa
    incoerenza — validazione più permissiva del codice che dovrebbe
    proteggere — dell'assenza di 'parcelle' al livello superiore, qui
    ripetuta un livello più sotto."""

    @pytest.mark.parametrize("nodi_non_validi", [None, [], "assente"])
    def test_nodi_mancante_vuota_o_none_solleva_errore_esplicito(self, config, nodi_non_validi):
        config_bad = copy.deepcopy(config)
        if nodi_non_validi == "assente":
            del config_bad["parcelle"][0]["nodi"]
        else:
            config_bad["parcelle"][0]["nodi"] = nodi_non_validi
        with pytest.raises(ValueError, match="nodi"):
            valida_config(config_bad, "normale", 1)

class TestNomeParcellaOCodiceNodoNonValido:
    """A differenza di colore_bacca/tipo_nodo (un insieme chiuso di
    alternative), nome parcella e codice nodo non hanno un insieme predefinito
    di valori validi: qualunque stringa "ragionevole" andrebbe bene per il
    solo scopo di identificare un nodo, ma non ogni stringa può attraversare
    indenne un topic MQTT, la routing key AMQP che ne deriva, e una riga di
    log — da cui il pattern ristretto, non un elenco di typo noti."""

    @pytest.mark.parametrize("nome_non_valido", [
        "parcella.A",       # punto: introduce un segmento in più nella routing key AMQP
        "parcella#A",       # cancelletto: wildcard di RabbitMQ per zero o più segmenti
        "parcella*A",       # asterisco: wildcard di RabbitMQ per un segmento singolo
        "parcella/A",       # slash: separatore di segmento nel topic MQTT stesso
        "parcella A",       # spazio: valido in YAML, non gestito da nessun consumatore a valle
        "",
        None,
    ])
    def test_nome_parcella_non_valido_solleva_errore(self, config, nome_non_valido):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["nome"] = nome_non_valido
        with pytest.raises(ValueError, match="Nome parcella non valido"):
            valida_config(config_bad, "normale", 1)

    @pytest.mark.parametrize("codice_non_valido", [
        "meteo-A1.evil#",   # lo stesso valore usato dalla controprova lato Java (MisurazioneListenerTest)
        "meteo/A1",
        "",
        None,
    ])
    def test_codice_nodo_non_valido_solleva_errore(self, config, codice_non_valido):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["nodi"][0]["codice"] = codice_non_valido
        with pytest.raises(ValueError, match="Codice nodo non valido"):
            valida_config(config_bad, "normale", 1)

    @pytest.mark.parametrize("valore_valido", ["parcellaA", "parcella_A", "parcella-A", "A1"])
    def test_valori_alfanumerici_con_underscore_o_trattino_passano(self, config, valore_valido):
        config_ok = copy.deepcopy(config)
        config_ok["parcelle"][0]["nome"] = valore_valido
        config_ok["parcelle"][0]["nodi"][0]["codice"] = valore_valido
        valida_config(config_ok, "normale", 1)

class TestColoreBaccaNonValido:
    @pytest.mark.parametrize("colore_typo", ["Nero", "NERO", "bianca", "rosso", "", None])
    def test_colore_bacca_con_typo_solleva_errore(self, config, colore_typo):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["colore_bacca"] = colore_typo
        with pytest.raises(ValueError, match="colore_bacca non valido"):
            valida_config(config_bad, "normale", 1)

    @pytest.mark.parametrize("colore_valido", COLORI_BACCA_VALIDI)
    def test_ciascun_colore_valido_passa(self, config, colore_valido):
        config_ok = copy.deepcopy(config)
        config_ok["parcelle"][0]["colore_bacca"] = colore_valido
        valida_config(config_ok, "normale", 1)


class TestTipoNodoNonValido:
    @pytest.mark.parametrize("tipo_typo", ["Meteo", "meteoo", "aria", ""])
    def test_tipo_nodo_con_typo_solleva_errore(self, config, tipo_typo):
        config_bad = copy.deepcopy(config)
        config_bad["parcelle"][0]["nodi"][0]["tipo"] = tipo_typo
        with pytest.raises(ValueError, match="tipo nodo non valido"):
            valida_config(config_bad, "normale", 1)

    @pytest.mark.parametrize("tipo_valido", TIPI_NODO_VALIDI)
    def test_ciascun_tipo_valido_passa(self, config, tipo_valido):
        config_ok = copy.deepcopy(config)
        config_ok["parcelle"][0]["nodi"][0]["tipo"] = tipo_valido
        valida_config(config_ok, "normale", 1)


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
            valida_config(config_bad, "normale", 1)

    def test_topic_prefix_atteso_passa(self, config):
        config_ok = copy.deepcopy(config)
        config_ok["mqtt"]["topic_prefix"] = TOPIC_PREFIX_ATTESO
        valida_config(config_ok, "normale", 1)

    def test_messaggio_nomina_i_file_java_da_aggiornare_insieme(self, config):
        config_bad = copy.deepcopy(config)
        config_bad["mqtt"]["topic_prefix"] = "altro"
        with pytest.raises(ValueError, match="RabbitConfig"):
            valida_config(config_bad, "normale", 1)


class TestTimeScaleNonValido:
    """A differenza degli altri campi, time_scale non passa per un confronto
    di uguaglianza ma per una divisione (sleep_reale) e una moltiplicazione
    (SimulatedClock): zero, i negativi, e i valori non finiti sono i valori
    pericolosi, non un insieme chiuso di alternative come scenario/colore.
    nan/inf meritano una classe di controprova a parte: 'nan <= 0' e
    'inf <= 0' sono entrambi False in Python (i confronti con un valore non
    finito non sollevano mai True), quindi un controllo che si fermasse al
    solo '<= 0' li lascerebbe passare senza accorgersene."""

    @pytest.mark.parametrize("time_scale_non_valido", [0, 0.0, -1, -2880])
    def test_zero_o_negativo_solleva_errore(self, config, time_scale_non_valido):
        with pytest.raises(ValueError, match="time_scale non valido"):
            valida_config(config, "normale", time_scale_non_valido)

    @pytest.mark.parametrize("time_scale_non_finito", [
        float("nan"), float("inf"), float("-inf"),
    ])
    def test_non_finito_solleva_errore(self, config, time_scale_non_finito):
        """--time-scale nan/inf/-inf: valori che argparse(type=float) accetta
        senza obiettare (sono float validi per Python), ma che 'sfuggono' al
        solo controllo '<= 0' — nan e inf non sono mai <= 0. Senza questo
        controllo, il crash arriverebbe più avanti, dentro
        SimulatedClock.now() (ValueError/OverflowError nella conversione a
        intero di un timedelta non finito), con un messaggio che non nomina
        affatto time_scale."""
        with pytest.raises(ValueError, match="time_scale non valido"):
            valida_config(config, "normale", time_scale_non_finito)

    @pytest.mark.parametrize("time_scale_valido", [1, 0.5, 2880])
    def test_positivo_passa(self, config, time_scale_valido):
        valida_config(config, "normale", time_scale_valido)

    def test_messaggio_nomina_il_valore_trovato(self, config):
        with pytest.raises(ValueError, match="-5"):
            valida_config(config, "normale", -5)

    @pytest.mark.parametrize("time_scale_non_numerico", [None, "2880", []])
    def test_non_numerico_solleva_errore_esplicito_non_typeerror(self, config, time_scale_non_numerico):
        """Raggiungibile da main() dopo che l'estrazione è diventata un
        .get() difensivo: un time_scale mancante dal YAML arriva qui come
        None, non più con un KeyError. Senza il controllo isinstance() prima
        di math.isfinite(), questo caso solleverebbe TypeError — un tipo di
        eccezione che il chiamante non si aspetta di dover intercettare
        insieme a ValueError."""
        with pytest.raises(ValueError, match="time_scale non valido"):
            valida_config(config, "normale", time_scale_non_numerico)

class TestIntervalloPubblicazioneNonValido:
    """Mai validato prima di questa classe: config["simulazione"]["intervallo_pubblicazione_secondi"]
    veniva letto con indicizzazione diretta in main(), quindi un valore
    assente o non numerico si sarebbe comunque fatto notare con un
    KeyError/TypeError — solo non con un messaggio che dicesse cosa non
    andava. Stessa natura di time_scale (finisce in una divisione), stesso
    tipo di controllo."""

    @pytest.mark.parametrize("valore_non_valido", [0, 0.0, -1, -900, float("nan"), float("inf"), None, "900"])
    def test_valore_non_valido_solleva_errore_esplicito(self, config, valore_non_valido):
        config_bad = copy.deepcopy(config)
        config_bad["simulazione"]["intervallo_pubblicazione_secondi"] = valore_non_valido
        with pytest.raises(ValueError, match="intervallo_pubblicazione_secondi non valido"):
            valida_config(config_bad, "normale", 1)

    @pytest.mark.parametrize("valore_valido", [1, 30, 900, 3600])
    def test_valore_positivo_passa(self, config, valore_valido):
        config_ok = copy.deepcopy(config)
        config_ok["simulazione"]["intervallo_pubblicazione_secondi"] = valore_valido
        valida_config(config_ok, "normale", 1)

class TestMessaggioErroreIdentificaIlCampo:
    """Un ValueError generico ('config non valida') costringerebbe a rileggere
    l'intero file per capire cosa correggere: il messaggio deve nominare
    esplicitamente il campo e il valore trovato."""

    def test_messaggio_scenario_contiene_il_valore_sbagliato(self, config):
        with pytest.raises(ValueError, match="ondata di calore"):
            valida_config(config, "ondata di calore", 1)

    def test_messaggio_colore_bacca_nomina_la_parcella(self, config):
        config_bad = copy.deepcopy(config)
        nome_parcella = config_bad["parcelle"][0]["nome"]
        config_bad["parcelle"][0]["colore_bacca"] = "Nero"
        with pytest.raises(ValueError, match=nome_parcella):
            valida_config(config_bad, "normale", 1)

    def test_messaggio_tipo_nodo_nomina_il_codice_nodo(self, config):
        config_bad = copy.deepcopy(config)
        codice_nodo = config_bad["parcelle"][0]["nodi"][0]["codice"]
        config_bad["parcelle"][0]["nodi"][0]["tipo"] = "meteoo"
        with pytest.raises(ValueError, match=codice_nodo):
            valida_config(config_bad, "normale", 1)