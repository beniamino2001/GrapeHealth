"""
Test su simulator/state.py: persistenza dello stato di sessione fra
esecuzioni.

STATE_PATH è una costante di modulo, non un parametro: ogni test la
sostituisce con monkeypatch puntandola dentro tmp_path, così nessun test
tocca mai la cartella .state/ reale del progetto.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

import json

import pytest

import simulator.state as state
from datetime import datetime


@pytest.fixture(autouse=True)
def state_path_isolato(tmp_path, monkeypatch):
    """Applicata automaticamente a ogni test di questo file: reindirizza
    STATE_PATH verso una cartella temporanea, isolando ogni test dagli
    altri e dal filesystem reale."""
    monkeypatch.setattr(state, "STATE_PATH", tmp_path / "sessione_simulata.json")


class TestCaricaStatoSessioneAssente:
    def test_ritorna_none_se_il_file_non_esiste(self):
        assert state.carica_stato_sessione() is None


class TestSalvaEPoiCarica:
    def test_round_trip_ritorna_gli_stessi_dati(self):
        ts = datetime(2026, 7, 4, 15, 30, 0)
        stati_parcelle = {
            "parcellaA": {"psi_stem": -1.1, "umidita_suolo": 22.0,
                          "pioggia_oggi_mm": 0.0, "ultimo_giorno": "2026-07-04"},
            "parcellaC": {"psi_stem": -0.9, "umidita_suolo": 25.0,
                          "pioggia_oggi_mm": 12.3, "ultimo_giorno": "2026-07-04"},
        }
        state.salva_stato_sessione(ts, stati_parcelle)

        caricato = state.carica_stato_sessione()

        assert caricato is not None
        assert caricato["ultimo_timestamp_simulato"] == ts.isoformat()
        assert caricato["parcelle"] == stati_parcelle

    def test_una_seconda_scrittura_sovrascrive_la_prima_non_la_accoda(self):
        state.salva_stato_sessione(datetime(2026, 7, 1), {"parcellaA": {"psi_stem": -0.9}})
        state.salva_stato_sessione(datetime(2026, 7, 2), {"parcellaA": {"psi_stem": -1.0}})

        caricato = state.carica_stato_sessione()

        assert caricato["ultimo_timestamp_simulato"] == datetime(2026, 7, 2).isoformat()
        assert caricato["parcelle"]["parcellaA"]["psi_stem"] == -1.0

    def test_crea_la_cartella_state_se_non_esiste_ancora(self, tmp_path, monkeypatch):
        percorso_annidato = tmp_path / "una_cartella_che_non_esiste_ancora" / "sessione_simulata.json"
        monkeypatch.setattr(state, "STATE_PATH", percorso_annidato)

        state.salva_stato_sessione(datetime(2026, 7, 1), {})

        assert percorso_annidato.exists()


class TestScritturaAtomica:
    """La scrittura passa da un file temporaneo (.tmp) rinominato sul file
    definitivo solo a scrittura completata: un'interruzione a metà lascia il
    file precedente intatto, mai un file a metà."""

    def test_il_file_temporaneo_non_resta_dopo_una_scrittura_riuscita(self):
        state.salva_stato_sessione(datetime(2026, 7, 1), {})

        percorso_temporaneo = state.STATE_PATH.with_suffix(".tmp")
        assert not percorso_temporaneo.exists(), (
            "Il file temporaneo dovrebbe essere stato rinominato sul percorso "
            "definitivo, non lasciato in giro dopo una scrittura riuscita."
        )
        assert state.STATE_PATH.exists()

    def test_una_scrittura_fallita_non_corrompe_lo_stato_precedente(self, monkeypatch):
        """Simula un errore a metà della seconda scrittura (dopo che la prima
        è già andata a buon fine): lo stato letto successivamente deve essere
        ancora quello della prima scrittura, mai uno stato a metà o assente."""
        state.salva_stato_sessione(datetime(2026, 7, 1), {"parcellaA": {"psi_stem": -0.9}})

        def replace_che_fallisce(self, target):
            raise OSError("simulato: disco pieno a metà scrittura")

        monkeypatch.setattr("pathlib.Path.replace", replace_che_fallisce)
        # Non deve propagare l'eccezione: salva_stato_sessione la intercetta e logga.
        state.salva_stato_sessione(datetime(2026, 7, 2), {"parcellaA": {"psi_stem": -1.5}})

        # Niente monkeypatch.undo() qui: annullerebbe anche il redirect di
        # STATE_PATH applicato dalla fixture autouse (stessa istanza di
        # monkeypatch), facendo leggere carica_stato_sessione() dal percorso
        # reale del progetto invece che da quello temporaneo di questo test.
        caricato = state.carica_stato_sessione()
        assert caricato["ultimo_timestamp_simulato"] == datetime(2026, 7, 1).isoformat()
        assert caricato["parcelle"]["parcellaA"]["psi_stem"] == -0.9


class TestStatoNonValido:
    """Un file mancante, corrotto o in un formato non riconosciuto va
    trattato come se non fosse mai esistito, non come un errore fatale."""

    def test_json_corrotto_ritorna_none_senza_sollevare_eccezioni(self):
        state.STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        state.STATE_PATH.write_text("{questo non e' json valido", encoding="utf-8")

        assert state.carica_stato_sessione() is None

    def test_json_valido_ma_senza_il_campo_indispensabile_ritorna_none(self):
        state.STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        state.STATE_PATH.write_text(json.dumps({"parcelle": {}}), encoding="utf-8")

        assert state.carica_stato_sessione() is None

    def test_timestamp_non_isoformat_ritorna_none(self):
        state.STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        state.STATE_PATH.write_text(
            json.dumps({"ultimo_timestamp_simulato": "non-e-una-data", "parcelle": {}}),
            encoding="utf-8",
        )

        assert state.carica_stato_sessione() is None


class TestEliminaStatoSessione:
    def test_rimuove_un_file_esistente(self):
        state.salva_stato_sessione(datetime(2026, 7, 1), {})
        assert state.STATE_PATH.exists()

        state.elimina_stato_sessione()

        assert not state.STATE_PATH.exists()

    def test_non_solleva_eccezioni_se_il_file_non_esiste(self):
        # Non deve sollevare nulla anche se non c'e' mai stato uno stato salvato.
        state.elimina_stato_sessione()

    def test_non_propaga_oserror_anche_per_cause_diverse_dal_file_mancante(self, monkeypatch):
        """missing_ok=True copre il caso comune (file assente); questo test
        copre il caso residuo (es. permessi, percorso non valido) in cui
        unlink() solleva comunque OSError."""
        def unlink_che_fallisce(self, missing_ok=False):
            raise OSError("simulato: permesso negato")

        monkeypatch.setattr("pathlib.Path.unlink", unlink_che_fallisce)

        state.elimina_stato_sessione()  # non deve sollevare nulla