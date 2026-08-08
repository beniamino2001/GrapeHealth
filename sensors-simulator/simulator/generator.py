"""
Modelli di generazione dei dati sintetici, ovvero euristiche pensate per produrre
serie plausibili (ciclo diurno, correlazione temp_bacca/temp_aria, deriva
del potenziale idrico nei giorni senza pioggia) ancorate agli ordini di
grandezza riportati in bibliografia (Gambetta et al., 2021; Acevedo-Opazo
et al., 2010; Schmidt et al., 2023).
"""

import math
import random
from datetime import datetime


class StatoParcella:
    """Mantiene la 'memoria' di una parcella tra una lettura e l'altra:
    il potenziale idrico e i giorni senza pioggia non sono funzioni
    istantanee dell'ora del giorno, ma evolvono nel tempo."""

    def __init__(self, scenario: str):
        self.scenario = scenario
        self.psi_stem = -0.85
        self.giorni_senza_pioggia = 0
        self.pioggia_oggi_mm = 0.0
        self._ultimo_giorno = None

    def aggiorna_se_nuovo_giorno(self, dt: datetime) -> None:
        giorno = dt.date().isoformat()
        if giorno == self._ultimo_giorno:
            return
        self._ultimo_giorno = giorno

        prob_pioggia = {
            "normale": 0.18,
            "ondata_di_calore": 0.08,
            "stress_idrico": 0.02,
        }[self.scenario]

        self.pioggia_oggi_mm = 0.0
        if random.random() < prob_pioggia:
            self.pioggia_oggi_mm = round(random.uniform(4, 22), 1)

        if self.pioggia_oggi_mm >= 10:
            # evento significativo: recupero del potenziale idrico
            self.psi_stem = min(-0.75, self.psi_stem + random.uniform(0.3, 0.5))
            self.giorni_senza_pioggia = 0
        elif self.pioggia_oggi_mm > 0:
            self.psi_stem = min(-0.75, self.psi_stem + 0.1)
            self.giorni_senza_pioggia = 0
        else:
            self.giorni_senza_pioggia += 1
            deriva = -0.06 if self.scenario == "stress_idrico" else -0.03
            self.psi_stem = max(-1.6, self.psi_stem + deriva)


def genera_temp_aria(dt: datetime, scenario: str) -> float:
    ora = dt.hour + dt.minute / 60
    media, ampiezza = 20.0, 8.0
    # picco intorno alle 15:00, minimo intorno alle 03:00
    valore = media + ampiezza * math.sin(2 * math.pi * (ora - 9) / 24)
    if scenario == "ondata_di_calore":
        valore += 8
    return round(valore + random.uniform(-0.5, 0.5), 1)


def genera_umidita_aria(temp_aria: float, scenario: str) -> float:
    valore = 75 - (temp_aria - 18) * 1.8
    if scenario == "ondata_di_calore":
        valore -= 10
    valore = max(20, min(95, valore + random.uniform(-3, 3)))
    return round(valore, 1)


def genera_bagnatura_fogliare(dt: datetime, pioggia_oggi_mm: float, umidita_aria: float) -> float:
    ora = dt.hour
    if pioggia_oggi_mm > 0:
        base = 90.0
    elif ora <= 8 or ora >= 21:
        base = 40 + (umidita_aria - 60) * 0.5  # rugiada notturna favorita da UR alta
    else:
        base = max(0.0, 20 - (ora - 8) * 3)  # si asciuga durante le ore centrali
    valore = max(0.0, min(100.0, base + random.uniform(-5, 5)))
    return round(valore, 1)


def genera_temp_bacca(temp_aria: float, dt: datetime, colore_bacca: str, scenario: str) -> float:
    ora = dt.hour + dt.minute / 60
    # sovratemperatura concentrata nelle ore di massima esposizione solare
    fattore_esposizione = max(0.0, math.sin(2 * math.pi * (ora - 9) / 24))
    offset_max = 14.0 if colore_bacca == "nero" else 10.0  # Gambetta et al., 2021
    offset = offset_max * fattore_esposizione
    if scenario == "ondata_di_calore":
        offset *= 1.3
    return round(temp_aria + offset + random.uniform(-0.4, 0.4), 1)


def genera_letture_nodo(tipo_nodo: str, dt: datetime, stato: StatoParcella, colore_bacca: str, temp_aria_riferimento: float):
    if tipo_nodo == "meteo":
        t = temp_aria_riferimento
        u = genera_umidita_aria(t, stato.scenario)
        b = genera_bagnatura_fogliare(dt, stato.pioggia_oggi_mm, u)
        return {
            "temperatura_aria": (t, "C"),
            "umidita_aria": (u, "%"),
            "pioggia": (stato.pioggia_oggi_mm, "mm"),
            "bagnatura_fogliare": (b, "%"),
        }
    if tipo_nodo == "idrico":
        rumore = round(random.uniform(-0.03, 0.03), 2)
        return {"psi_stem": (round(stato.psi_stem + rumore, 2), "MPa")}
    if tipo_nodo == "bacca":
        return {"temperatura_bacca": (genera_temp_bacca(temp_aria_riferimento, dt, colore_bacca, stato.scenario), "C")}
    raise ValueError(f"Tipo nodo sconosciuto: {tipo_nodo}")