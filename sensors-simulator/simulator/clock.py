"""
Orologio simulato: disaccoppia il tempo "di dominio" (ora del giorno, ecc.)
dal tempo reale di esecuzione. Con time_scale=1 coincidono; con time_scale>1
un secondo reale vale time_scale secondi simulati (utile per attraversare
in fretta un ciclo diurno o più giorni senza pioggia).

Avviso per chi consuma timestamp_rilevazione: con time_scale != 1 il valore
si allontana rapidamente dall'ora reale della macchina. Un confronto con
"adesso" preso dall'orologio reale (es. `new Date()`, `datetime.now()` non
esplicito) invece che dall'ultimo timestamp osservato nel flusso troverà
risultati vuoti non appena time_scale supera 1: non è un bug, è la
conseguenza diretta di cosa time_scale significa. Il riferimento ad
"adesso" deve venire dai dati, mai dall'orologio del consumatore.
"""

import time
from datetime import datetime, timedelta, timezone


class SimulatedClock:
    def __init__(self, time_scale: float = 1.0, start: datetime | None = None):
        self.time_scale = time_scale
        self._start_real = time.monotonic()
        self._start_sim = start or datetime.now(timezone.utc).replace(tzinfo=None)

    def now(self) -> datetime:
        elapsed_real = time.monotonic() - self._start_real
        elapsed_sim = elapsed_real * self.time_scale
        return self._start_sim + timedelta(seconds=elapsed_sim)