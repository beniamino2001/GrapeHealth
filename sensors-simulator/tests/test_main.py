"""
Test su simulator/main.py: carica_config() e parse_args().

main() stesso non è testato qui: orchestra connessione MQTT reale e un
ciclo infinito, un caso da run end-to-end sull'intero stack, non da test
unitario. valida_config() ha la propria suite dedicata in
test_config_validation.py.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

import json
import signal
import sys

import pytest

from simulator.main import (
    SCENARI_VALIDI,
    avviso_intervallo_sovrascritto,
    avviso_time_scale,
    carica_config,
    corrompi_payload,
    gestisci_sigterm,
    parse_args,
    valida_tasso_errori,
    valore_effettivo,
)


class TestCaricaConfig:
    def test_ritorna_un_dizionario_con_le_sezioni_principali(self):
        config = carica_config()

        assert "parcelle" in config
        assert "simulazione" in config
        assert "mqtt" in config
        assert len(config["parcelle"]) == 3


class TestGestisceSigterm:
    """gestisci_sigterm() è la funzione pura registrata come handler di
    SIGTERM in main() (non testato qui, v. docstring del modulo): estratta
    apposta per essere verificabile senza inviare un segnale POSIX reale,
    sullo stesso principio già applicato a avviso_time_scale()."""

    def test_solleva_keyboard_interrupt(self):
        with pytest.raises(KeyboardInterrupt):
            gestisci_sigterm(signal.SIGTERM, None)


class TestParseArgs:
    def _con_argv(self, monkeypatch, *argomenti):
        monkeypatch.setattr(sys, "argv", ["main.py", *argomenti])

    def test_senza_argomenti_i_default_sono_none_o_false(self, monkeypatch):
        self._con_argv(monkeypatch)

        args = parse_args()

        assert args.scenario is None
        assert args.time_scale is None
        assert args.reset_sessione is False
        assert args.tasso_errori == 0.0

    def test_scenario_valido_viene_accettato(self, monkeypatch):
        self._con_argv(monkeypatch, "--scenario", "ondata_di_calore")

        args = parse_args()

        assert args.scenario == "ondata_di_calore"

    @pytest.mark.parametrize("scenario_invalido", ["ondata di calore", "Normale", "caldo"])
    def test_scenario_non_valido_da_cli_viene_rifiutato_da_argparse(self, monkeypatch, scenario_invalido, capsys):
        """argparse deve fermare un typo passato da riga di comando prima
        ancora che valida_config() entri in gioco: choices= lo garantisce."""
        self._con_argv(monkeypatch, "--scenario", scenario_invalido)

        with pytest.raises(SystemExit):
            parse_args()

        errore = capsys.readouterr().err
        assert "--scenario" in errore

    def test_time_scale_viene_convertito_a_float(self, monkeypatch):
        self._con_argv(monkeypatch, "--time-scale", "2880")

        args = parse_args()

        assert args.time_scale == 2880.0
        assert isinstance(args.time_scale, float)

    def test_reset_sessione_e_un_flag_booleano(self, monkeypatch):
        self._con_argv(monkeypatch, "--reset-sessione")

        args = parse_args()

        assert args.reset_sessione is True

    def test_tutti_gli_scenari_dichiarati_validi_sono_accettati_da_argparse(self, monkeypatch):
        """Le scelte di argparse (choices=) devono restare in sincronia con
        SCENARI_VALIDI: se qualcuno le disallineasse per errore, questo test
        lo segnala."""
        for scenario in SCENARI_VALIDI:
            self._con_argv(monkeypatch, "--scenario", scenario)
            args = parse_args()
            assert args.scenario == scenario

    def test_tasso_errori_viene_convertito_a_float(self, monkeypatch):
        self._con_argv(monkeypatch, "--tasso-errori", "0.05")

        args = parse_args()

        assert args.tasso_errori == 0.05
        assert isinstance(args.tasso_errori, float)


class TestValidaTassoErrori:
    """Stessa disciplina di valida_config(): fallire subito e rumorosamente
    su un valore fuori dominio, non lasciare che random.random() < tasso
    si comporti in modo silenziosamente sbagliato con un valore assurdo."""

    @pytest.mark.parametrize("tasso_valido", [0.0, 0.5, 1.0])
    def test_valore_nel_range_passa(self, tasso_valido):
        valida_tasso_errori(tasso_valido)  # non deve sollevare nulla

    @pytest.mark.parametrize("tasso_non_valido", [-0.1, 1.1, -5, 50])
    def test_valore_fuori_range_solleva_errore(self, tasso_non_valido):
        with pytest.raises(ValueError, match="tasso-errori"):
            valida_tasso_errori(tasso_non_valido)


class TestCorrompiPayload:
    """corrompi_payload(): usata solo quando --tasso-errori è maggiore di
    zero, per esercitare le code di dead-letter dei moduli a valle con
    traffico reale invece che con messaggi costruiti a mano."""

    def _payload(self, nodo="meteo-A1"):
        return {
            "nodo": nodo, "parcella": "parcellaA", "parametro": "temperatura_aria",
            "valore": 28.5, "unita_misura": "C", "timestamp_rilevazione": "2026-07-01T12:00:00Z",
        }

    def test_produce_una_stringa_non_json_valida(self):
        corrotto = corrompi_payload(self._payload())

        with pytest.raises(json.JSONDecodeError):
            json.loads(corrotto)

    def test_conserva_il_nodo_di_origine_per_restare_identificabile_in_dead_letter(self):
        corrotto = corrompi_payload(self._payload(nodo="bacca-C1"))

        assert "bacca-C1" in corrotto


class TestAvvisoTimeScale:
    def test_nessun_avviso_a_time_scale_1(self):
        assert avviso_time_scale(1) is None
        assert avviso_time_scale(1.0) is None

    def test_avviso_presente_sopra_1(self):
        messaggio = avviso_time_scale(2880)
        assert messaggio is not None
        assert "2880" in messaggio

    def test_avviso_presente_anche_sotto_1(self):
        """Un time_scale frazionario rallenta il tempo simulato rispetto a
        quello reale: la divergenza esiste in entrambe le direzioni, non
        solo quando si accelera."""
        messaggio = avviso_time_scale(0.5)
        assert messaggio is not None


class TestValoreEffettivo:
    """Sostituisce `da_cli or da_config`: quell'or fallirebbe silenziosamente
    su --time-scale 0, dato che 0 è falsy in Python — esattamente il valore
    che dovrebbe invece essere rifiutato esplicitamente da valida_config(),
    non silenziosamente sostituito dal default di config."""

    def test_valore_cli_assente_usa_il_config(self):
        assert valore_effettivo(None, "normale") == "normale"
        assert valore_effettivo(None, 1) == 1

    def test_valore_cli_presente_lo_usa_anche_se_falsy(self):
        assert valore_effettivo(0, 2880) == 0
        assert valore_effettivo(0.0, 2880) == 0.0

    def test_valore_cli_presente_e_normale_lo_usa(self):
        assert valore_effettivo("ondata_di_calore", "normale") == "ondata_di_calore"
        assert valore_effettivo(2880, 1) == 2880


class TestAvvisoIntervalloSovrascritto:
    """Sotto il pavimento di 0,1s su sleep_reale, l'intervallo simulato
    realmente attraversato fra due tick diventa 0,1*time_scale, non più
    quello dichiarato in config — una condizione che resta vera per poche
    ore simulate genera così molte più occasioni di riallerta di quante
    l'intervallo configurato lascerebbe pensare."""

    def test_nessun_avviso_se_intervallo_sopra_il_pavimento(self):
        # 300/2880 = 0.104s, sopra il pavimento di 0.1s
        assert avviso_intervallo_sovrascritto(300, 2880) is None

    def test_nessun_avviso_a_time_scale_1(self):
        assert avviso_intervallo_sovrascritto(30, 1) is None

    def test_avviso_presente_sotto_il_pavimento(self):
        # 30/2880 = 0.0104s, sotto il pavimento di 0.1s
        messaggio = avviso_intervallo_sovrascritto(30, 2880)
        assert messaggio is not None
        assert "30" in messaggio
        assert "288" in messaggio  # intervallo realmente attraversato: 0.1*2880

    def test_avviso_al_limite_esatto_del_pavimento(self):
        # 288/2880 = 0.1s esatti: non sotto il pavimento, nessun avviso
        assert avviso_intervallo_sovrascritto(288, 2880) is None
        # un secondo sotto: 287/2880 < 0.1s, avviso presente
        assert avviso_intervallo_sovrascritto(287, 2880) is not None