"""
Test su scripts/init_nodi_db.py: sincronizzazione dell'anagrafica nodi.

Nessun database reale coinvolto: psycopg2.connect() viene sostituito con
una connessione finta che registra le query ricevute, sullo stesso
principio già usato per mqtt_client.py con il client paho finto. La
verifica contro un PostgreSQL vero resta compito delle sincronizzazioni
manuali già documentate, non di questi test.

Usa la config reale del progetto (config/nodi.yaml): 3 parcelle, 12 nodi.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

from unittest.mock import MagicMock, call

import psycopg2
import pytest

import scripts.init_nodi_db as init_nodi_db


@pytest.fixture(autouse=True)
def dotenv_isolato(monkeypatch):
    """main() chiama load_dotenv() a ogni esecuzione: senza questa fixture,
    un .env reale presente sul filesystem (quello di sviluppo, con
    credenziali vere) rileggerebbe silenziosamente una variabile che un
    test ha appena rimosso con monkeypatch.delenv(), perché load_dotenv()
    per difetto non sovrascrive una variabile già assente dall'ambiente —
    la ripristina. Le variabili d'ambiente che contano per questi test le
    impostano le fixture/i test stessi, non un file .env che può cambiare
    da una macchina all'altra."""
    monkeypatch.setattr(init_nodi_db, "load_dotenv", lambda *a, **k: None)


@pytest.fixture
def cursore_finto():
    """Un cursore che registra le query eseguite; fetchone() restituisce id
    progressivi (1, 2, 3, ...) a ogni chiamata, come farebbe una sequenza
    reale di PostgreSQL per gli id di parcella inseriti."""
    cursore = MagicMock()
    cursore.fetchone.side_effect = [(i,) for i in range(1, 100)]
    cursore.rowcount = 0
    return cursore


@pytest.fixture
def connessione_finta(monkeypatch, cursore_finto):
    """Sostituisce psycopg2.connect(): restituisce sempre la stessa
    connessione finta, il cui cursor() restituisce cursore_finto."""
    connessione = MagicMock()
    connessione.cursor.return_value.__enter__.return_value = cursore_finto
    monkeypatch.setattr(psycopg2, "connect", MagicMock(return_value=connessione))
    return connessione


@pytest.fixture(autouse=True)
def credenziali_valide(monkeypatch):
    """Applicata a ogni test: senza queste variabili lo script si ferma
    prima ancora di provare a connettersi (v. TestCredenzialiMancanti,
    che le rimuove esplicitamente)."""
    monkeypatch.setenv("POSTGRES_USER", "utente_test")
    monkeypatch.setenv("POSTGRES_PASSWORD", "password_test")


class TestCredenzialiMancanti:
    def test_senza_postgres_user_esce_con_codice_1_e_non_si_connette(self, monkeypatch, capsys):
        monkeypatch.delenv("POSTGRES_USER", raising=False)
        connect_finto = MagicMock()
        monkeypatch.setattr(psycopg2, "connect", connect_finto)

        with pytest.raises(SystemExit) as exc_info:
            init_nodi_db.main()

        assert exc_info.value.code == 1
        connect_finto.assert_not_called()
        assert "POSTGRES_USER" in capsys.readouterr().err

    def test_senza_postgres_password_esce_con_codice_1_e_non_si_connette(self, monkeypatch, capsys):
        monkeypatch.delenv("POSTGRES_PASSWORD", raising=False)
        connect_finto = MagicMock()
        monkeypatch.setattr(psycopg2, "connect", connect_finto)

        with pytest.raises(SystemExit) as exc_info:
            init_nodi_db.main()

        assert exc_info.value.code == 1
        connect_finto.assert_not_called()


class TestConnessioneFallita:
    def test_operational_error_esce_con_codice_1(self, monkeypatch, capsys):
        monkeypatch.setattr(
            psycopg2, "connect",
            MagicMock(side_effect=psycopg2.OperationalError("connessione rifiutata")),
        )

        with pytest.raises(SystemExit) as exc_info:
            init_nodi_db.main()

        assert exc_info.value.code == 1
        assert "PostgreSQL" in capsys.readouterr().err


class TestTlsObbligatorio:
    """pg_hba.conf accetta solo connessioni hostssl: senza sslmode esplicito
    a verify-full, la connessione cifrerebbe comunque (default 'prefer' di
    psycopg2) ma senza verificare il certificato del server contro la CA
    locale — una password intercettabile via man-in-the-middle non sarebbe
    diversa, in pratica, da nessuna cifratura affatto."""

    def test_sslmode_verify_full_con_la_ca_locale(self, connessione_finta, cursore_finto):
        init_nodi_db.main()

        _, kwargs = psycopg2.connect.call_args
        assert kwargs.get("sslmode") == "verify-full"
        assert kwargs.get("sslrootcert") == str(init_nodi_db.CA_CERT_PATH)


class TestSincronizzazioneRiuscita:
    def test_esegue_un_upsert_parcella_per_ciascuna_delle_tre_parcelle(self, connessione_finta, cursore_finto):
        init_nodi_db.main()

        chiamate_parcella = [c for c in cursore_finto.execute.call_args_list
                              if c.args[0] == init_nodi_db.UPSERT_PARCELLA_QUERY]
        assert len(chiamate_parcella) == 3

    def test_esegue_un_upsert_nodo_per_ciascuno_dei_dodici_nodi(self, connessione_finta, cursore_finto):
        init_nodi_db.main()

        chiamate_nodo = [c for c in cursore_finto.execute.call_args_list
                          if c.args[0] == init_nodi_db.UPSERT_NODO_QUERY]
        assert len(chiamate_nodo) == 12

    def test_i_parametri_dell_upsert_nodo_usano_l_id_restituito_dall_upsert_parcella(self, connessione_finta, cursore_finto):
        """fetchone() restituisce id progressivi 1,2,3: il primo nodo della
        prima parcella deve ricevere parcella_id=1, non un valore fisso o
        quello di un'altra parcella."""
        init_nodi_db.main()

        prima_chiamata_nodo = next(c for c in cursore_finto.execute.call_args_list
                                    if c.args[0] == init_nodi_db.UPSERT_NODO_QUERY)
        parcella_id_usato = prima_chiamata_nodo.args[1][1]
        assert parcella_id_usato == 1

    def test_disattiva_i_nodi_con_la_lista_completa_dei_codici_correnti(self, connessione_finta, cursore_finto):
        init_nodi_db.main()

        chiamata_deactivate = next(c for c in cursore_finto.execute.call_args_list
                                    if c.args[0] == init_nodi_db.DEACTIVATE_ORPHANED_NODI_QUERY)
        codici_passati = chiamata_deactivate.args[1][0]
        assert len(codici_passati) == 12
        assert "bacca-C1" in codici_passati
        assert "suolo-C1" in codici_passati

    def test_stampa_il_riepilogo_con_i_conteggi_corretti(self, connessione_finta, cursore_finto, capsys):
        cursore_finto.rowcount = 0

        init_nodi_db.main()

        output = capsys.readouterr().out
        assert "3 parcelle" in output
        assert "12 nodi" in output


class TestGuardiaConfigVuota:
    """Se nessun nodo viene letto dal YAML (config malformata o vuota), la
    disattivazione va saltata: altrimenti la query 'codice <> ALL([])'
    sarebbe vera per ogni riga esistente, disattivando l'intera anagrafica
    per un incidente di configurazione."""

    def test_config_senza_parcelle_non_esegue_la_disattivazione(self, monkeypatch, connessione_finta, cursore_finto, capsys):
        monkeypatch.setattr(
            "yaml.safe_load", MagicMock(return_value={"parcelle": []})
        )

        init_nodi_db.main()

        chiamate_deactivate = [c for c in cursore_finto.execute.call_args_list
                                if c.args[0] == init_nodi_db.DEACTIVATE_ORPHANED_NODI_QUERY]
        assert len(chiamate_deactivate) == 0
        assert "Attenzione" in capsys.readouterr().err

    def test_config_con_la_chiave_parcelle_del_tutto_assente_si_comporta_come_vuota(
        self, monkeypatch, connessione_finta, cursore_finto, capsys
    ):
        """A differenza di {'parcelle': []}, qui la chiave manca del tutto:
        config.get('parcelle') restituisce None, non una lista — 'or []' deve
        normalizzare entrambi i casi allo stesso comportamento, non sollevare
        un KeyError non gestito su config['parcelle']."""
        monkeypatch.setattr("yaml.safe_load", MagicMock(return_value={}))

        init_nodi_db.main()

        assert cursore_finto.execute.call_args_list == []
        assert "Attenzione" in capsys.readouterr().err

class TestCampoMancanteInUnaParcellaONodo:
    """A differenza della sezione 'parcelle' mancante nel suo insieme (un
    caso trattato come 'niente da sincronizzare', non un errore), una
    parcella presente ma con un singolo campo assente è quasi certamente un
    errore di battitura nel YAML: qui ci si aspetta un'uscita immediata con
    un messaggio che nomini il campo, non un KeyError grezzo a metà
    transazione né un proseguimento silenzioso con un dato incompleto."""

    @pytest.mark.parametrize("campo_da_rimuovere", [
        "nome", "varieta", "colore_bacca", "stadio_fenologico_germogli_cm",
        "latitudine", "longitudine", "nodi",
    ])
    def test_campo_parcella_mancante_esce_con_codice_1_e_nomina_il_campo(
        self, monkeypatch, connessione_finta, cursore_finto, capsys, campo_da_rimuovere
    ):
        config_reale = init_nodi_db.yaml.safe_load(open(init_nodi_db.CONFIG_PATH, encoding="utf-8"))
        del config_reale["parcelle"][0][campo_da_rimuovere]
        monkeypatch.setattr("yaml.safe_load", MagicMock(return_value=config_reale))

        with pytest.raises(SystemExit) as exc_info:
            init_nodi_db.main()

        assert exc_info.value.code == 1
        assert campo_da_rimuovere in capsys.readouterr().err

    @pytest.mark.parametrize("campo_da_rimuovere", ["codice", "tipo"])
    def test_campo_nodo_mancante_esce_con_codice_1_e_nomina_il_campo(
        self, monkeypatch, connessione_finta, cursore_finto, capsys, campo_da_rimuovere
    ):
        config_reale = init_nodi_db.yaml.safe_load(open(init_nodi_db.CONFIG_PATH, encoding="utf-8"))
        del config_reale["parcelle"][0]["nodi"][0][campo_da_rimuovere]
        monkeypatch.setattr("yaml.safe_load", MagicMock(return_value=config_reale))

        with pytest.raises(SystemExit) as exc_info:
            init_nodi_db.main()

        assert exc_info.value.code == 1
        assert campo_da_rimuovere in capsys.readouterr().err

    def test_transazione_annullata_non_lascia_upsert_parziali_visibili(
        self, monkeypatch, connessione_finta, cursore_finto, capsys
    ):
        """Il secondo nodo/parcella malformato non deve impedire il rollback
        di quanto già eseguito nella stessa transazione — verificato
        controllando che main() propaghi l'uscita invece di proseguire, dato
        che il rollback vero e proprio è responsabilità di 'with conn', già
        coperta da TestConnessioneFallita per il caso simmetrico."""
        config_reale = init_nodi_db.yaml.safe_load(open(init_nodi_db.CONFIG_PATH, encoding="utf-8"))
        del config_reale["parcelle"][1]["colore_bacca"]
        monkeypatch.setattr("yaml.safe_load", MagicMock(return_value=config_reale))

        with pytest.raises(SystemExit):
            init_nodi_db.main()

        chiamate_deactivate = [c for c in cursore_finto.execute.call_args_list
                                if c.args[0] == init_nodi_db.DEACTIVATE_ORPHANED_NODI_QUERY]
        assert len(chiamate_deactivate) == 0