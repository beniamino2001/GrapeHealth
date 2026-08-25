"""
Test su simulator/clock.py: l'orologio simulato che disaccoppia il tempo
di dominio (ora del giorno, giorni trascorsi) dal tempo reale di
esecuzione.

time.monotonic() viene sempre sostituito con un contatore controllato dal
test, mai fatto scorrere per davvero: un test che dovesse aspettare tempo
reale per verificare time_scale sarebbe lento e comunque impreciso.

Eseguire con: pytest -v (dalla cartella sensors-simulator/)
"""

from datetime import datetime, timedelta, timezone

import pytest

from simulator.clock import SimulatedClock


class OrologioControllato:
    """Sostituisce time.monotonic(): parte da un valore arbitrario e avanza
    solo quando il test lo decide esplicitamente con avanza(), mai da solo."""

    def __init__(self, valore_iniziale: float = 1000.0):
        self._valore = valore_iniziale

    def __call__(self) -> float:
        return self._valore

    def avanza(self, secondi: float) -> None:
        self._valore += secondi


@pytest.fixture
def orologio_reale_controllato(monkeypatch):
    orologio = OrologioControllato()
    monkeypatch.setattr("simulator.clock.time.monotonic", orologio)
    return orologio


class TestTimeScaleUno:
    def test_a_time_scale_1_il_tempo_simulato_avanza_come_il_reale(self, orologio_reale_controllato):
        inizio = datetime(2026, 7, 1, 10, 0, 0)
        clock = SimulatedClock(time_scale=1.0, start=inizio)

        orologio_reale_controllato.avanza(30)

        assert clock.now() == inizio + timedelta(seconds=30)


class TestTimeScaleCompresso:
    def test_time_scale_moltiplica_il_tempo_reale_trascorso(self, orologio_reale_controllato):
        inizio = datetime(2026, 7, 1, 0, 0, 0)
        clock = SimulatedClock(time_scale=2880, start=inizio)

        orologio_reale_controllato.avanza(10)  # 10s reali

        # 10s reali * 2880 = 28800s simulati = 8 ore
        assert clock.now() == inizio + timedelta(hours=8)

    def test_time_scale_frazionario_rallenta_il_tempo_simulato(self, orologio_reale_controllato):
        inizio = datetime(2026, 7, 1, 0, 0, 0)
        clock = SimulatedClock(time_scale=0.5, start=inizio)

        orologio_reale_controllato.avanza(10)

        assert clock.now() == inizio + timedelta(seconds=5)


class TestPuntoDiPartenza:
    def test_start_esplicito_e_il_valore_restituito_a_tempo_reale_zero(self, orologio_reale_controllato):
        punto_di_ripresa = datetime(2026, 7, 15, 8, 30, 0)
        clock = SimulatedClock(time_scale=2880, start=punto_di_ripresa)

        # Nessun avanzamento del tempo reale: now() deve coincidere esattamente
        # col punto di ripresa passato, non con "adesso".
        assert clock.now() == punto_di_ripresa

    def test_senza_start_esplicito_now_e_vicino_al_momento_di_costruzione(self):
        prima = datetime.now(timezone.utc).replace(tzinfo=None)
        clock = SimulatedClock(time_scale=1.0)
        dopo = datetime.now(timezone.utc).replace(tzinfo=None)

        # Nessun controllo su time.monotonic in questo test: verifica solo
        # che il valore di default sia "adesso", entro il tempo impiegato
        # a eseguire queste tre righe.
        assert prima <= clock.now() <= dopo + timedelta(seconds=1)


class TestMonotonia:
    def test_now_non_torna_mai_indietro_nel_tempo(self, orologio_reale_controllato):
        clock = SimulatedClock(time_scale=100, start=datetime(2026, 7, 1))

        letture = [clock.now()]
        for _ in range(5):
            orologio_reale_controllato.avanza(1)
            letture.append(clock.now())

        assert letture == sorted(letture), "now() ha prodotto una lettura fuori ordine cronologico."
        assert len(set(letture)) == len(letture), "now() ha restituito due letture identiche invece di avanzare."