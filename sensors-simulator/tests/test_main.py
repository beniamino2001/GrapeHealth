"""
Test su simulator/main.py: carica_config() e parse_args().

main() stesso non è testato qui: orchestra connessione MQTT reale e un
ciclo infinito, un caso da run end-to-end sull'intero stack, non da test
unitario. valida_config() ha la propria suite dedicata in
test_config_validation.py.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

import sys

import pytest

from simulator.main import (
    SCENARI_VALIDI,
    avviso_intervallo_sovrascritto,
    avviso_time_scale,
    carica_config,
    parse_args,
    valore_effettivo,
)


class TestCaricaConfig:
    def test_ritorna_un_dizionario_con_le_sezioni_principali(self):
        config = carica_config()

        assert "parcelle" in config
        assert "simulazione" in config
        assert "mqtt" in config
        assert len(config["parcelle"]) == 3


class TestParseArgs:
    def _con_argv(self, monkeypatch, *argomenti):
        monkeypatch.setattr(sys, "argv", ["main.py", *argomenti])

    def test_senza_argomenti_i_default_sono_none_o_false(self, monkeypatch):
        self._con_argv(monkeypatch)

        args = parse_args()

        assert args.scenario is None
        assert args.time_scale is None
        assert args.reset_sessione is False

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