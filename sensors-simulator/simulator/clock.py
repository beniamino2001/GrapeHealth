"""
Orologio simulato per disaccoppiare il tempo "di dominio" (usato per i modelli
di generazione dei dati: ora del giorno, giorni senza pioggia, ecc.) dal
tempo reale di esecuzione dello script.

Con time_scale=1 il tempo simulato coincide col tempo reale (comodo per
lasciare il simulatore acceso per ore/giorni e osservare il comportamento
"vero"). Con time_scale>1 un secondo reale vale time_scale secondi
simulati: utile nei test rapidi per attraversare in pochi minuti un ciclo
diurno completo o più giorni consecutivi senza pioggia.
"""

import time
from datetime import datetime, timedelta


class SimulatedClock:
    def __init__(self, time_scale: float = 1.0, start: datetime | None = None):
        self.time_scale = time_scale
        self._start_real = time.monotonic()
        self._start_sim = start or datetime.utcnow()

    def now(self) -> datetime:
        elapsed_real = time.monotonic() - self._start_real
        elapsed_sim = elapsed_real * self.time_scale
        return self._start_sim + timedelta(seconds=elapsed_sim)
