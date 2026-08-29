"""
Generazione dati sintetici: euristiche calibrate su ordini di grandezza
di letteratura (ciclo diurno, correlazione temp_bacca/temp_aria, deriva
idrica), non modelli predittivi validati.
"""

import math
import random
from datetime import datetime


class StatoParcella:
    """Stato che sopravvive fra una lettura e l'altra: psi_stem e pioggia
    non sono funzioni istantanee dell'ora, evolvono nel tempo.

    Attenzione: psi_stem, pioggia e umidita_suolo cambiano una volta al
    giorno (in aggiorna_se_nuovo_giorno), non a ogni pubblicazione — restano
    identici per ~2880 letture consecutive. "pioggia" è il totale del
    giorno, non un incremento: sommare ogni lettura come fosse indipendente
    conta lo stesso evento centinaia di volte.
    """

    def __init__(self, scenario: str):
        self.scenario = scenario
        self.psi_stem = -0.85
        self.pioggia_oggi_mm = 0.0
        self.umidita_suolo = 25.0  # % contenuto idrico volumetrico grezzo — nessuna soglia bibliografica
        # diretta: FTSW=0,4 (letteratura) è un indice normalizzato, richiederebbe capacità
        # di campo/punto di appassimento mai definiti per queste parcelle.
        self._ultimo_giorno = None

    def esporta_stato(self) -> dict:
        """Istantanea serializzabile in JSON dello stato fisico corrente,
        da passare a salva_stato_sessione() in state.py."""
        return {
            "psi_stem": self.psi_stem,
            "pioggia_oggi_mm": self.pioggia_oggi_mm,
            "umidita_suolo": self.umidita_suolo,
            "ultimo_giorno": self._ultimo_giorno,
        }

    def ripristina_stato(self, dati: dict) -> None:
        """Inverso di esporta_stato(): usato all'avvio per riprendere da dove
        una sessione precedente si era interrotta, invece di ripartire dalle
        condizioni di default (-0.85 MPa, ...).
        Lo scenario NON viene ripristinato: resta quello scelto per la
        sessione corrente, per permettere di cambiare scenario fra una
        sessione e l'altra pur continuando la stessa linea temporale."""
        self.psi_stem = dati.get("psi_stem", self.psi_stem)
        self.pioggia_oggi_mm = dati.get("pioggia_oggi_mm", self.pioggia_oggi_mm)
        self.umidita_suolo = dati.get("umidita_suolo", self.umidita_suolo)
        self._ultimo_giorno = dati.get("ultimo_giorno", self._ultimo_giorno)

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
            # evento significativo: recupero del potenziale idrico e del suolo
            self.psi_stem = min(-0.75, self.psi_stem + random.uniform(0.3, 0.5))
            self.umidita_suolo = min(40.0, self.umidita_suolo + random.uniform(6.0, 10.0))
        elif self.pioggia_oggi_mm > 0:
            self.psi_stem = min(-0.75, self.psi_stem + 0.1)
            self.umidita_suolo = min(40.0, self.umidita_suolo + random.uniform(1.5, 3.0))
        else:
            deriva = -0.06 if self.scenario == "stress_idrico" else -0.03
            self.psi_stem = max(-1.6, self.psi_stem + deriva)
            deriva_suolo = -0.8 if self.scenario == "stress_idrico" else -0.4
            self.umidita_suolo = max(10.0, self.umidita_suolo + deriva_suolo)


def genera_temp_aria(dt: datetime, scenario: str) -> float:
    ora = dt.hour + dt.minute / 60
    media, ampiezza = 20.0, 8.0
    # picco intorno alle 15:00, minimo intorno alle 03:00
    valore = media + ampiezza * math.sin(2 * math.pi * (ora - 9) / 24)
    if scenario == "ondata_di_calore":
        valore += 13  # tetto ~41,5°C: raggiunge la soglia severo di RegolaOndataDiCalore (40°C, Luo et al. 2011)
    return round(valore + random.uniform(-0.5, 0.5), 1)


def genera_umidita_aria(temp_aria: float, scenario: str) -> float:
    valore = 80 - (temp_aria - 18) * 1.8
    if scenario == "ondata_di_calore":
        valore -= 10
    valore = max(20, min(95, valore + random.uniform(-3, 3)))
    return round(valore, 1)


def genera_bagnatura_fogliare(dt: datetime, pioggia_oggi_mm: float, umidita_aria: float) -> float:
    """Il ramo diurno riusa lo stesso livello del ramo notturno (40 + (umidita_aria-60)*0,5),
    modulato da un coseno rialzato che vale 1 esattamente alle 8 e alle 21 — qualunque sia
    umidita_aria — e 0 nel punto centrale (14:30): garantisce che il valore non faccia mai
    un salto ai due confini fra i due rami, invece di ripartire da una soglia fissa (20)
    scollegata dal livello notturno realmente raggiunto in quel momento."""
    ora = dt.hour + dt.minute / 60
    livello_notturno = 40 + (umidita_aria - 60) * 0.5  # rugiada notturna favorita da UR alta
    if pioggia_oggi_mm > 0:
        base = 90.0
    elif ora <= 8 or ora >= 21:
        base = livello_notturno
    else:
        fattore_diurno = (1 + math.cos(2 * math.pi * (ora - 8) / 13)) / 2  # 1 alle 8/21, 0 alle 14:30
        base = max(0.0, livello_notturno * fattore_diurno)  # si asciuga durante le ore centrali
    valore = max(0.0, min(100.0, base + random.uniform(-5, 5)))
    return round(valore, 1)


def genera_velocita_vento(dt: datetime, scenario: str) -> float:
    """Vento (m/s): alimenta il raffreddamento di temperatura_bacca in
    genera_temp_bacca() (stesso valore, stesso tick — v. velocita_vento_riferimento
    in main.py). Unico uso bibliografico noto: per la peronospora il vento
    resta solo qualitativo, nessuna soglia quantitativa in letteratura.
    Più calmo di notte, ridotto in ondata di calore (aria stagnante).
    """
    ora = dt.hour + dt.minute / 60
    base = 1.0 + 1.5 * max(0.0, math.sin(2 * math.pi * (ora - 7) / 24))
    if scenario == "ondata_di_calore":
        base *= 0.6
    valore = max(0.0, base + random.uniform(-0.5, 1.5))
    return round(valore, 1)


def genera_temperatura_suolo(dt: datetime, scenario: str) -> float:
    """Suolo (°C): stesso principio dell'aria ma inerzia termica maggiore —
    ampiezza ridotta (3°C contro 8°C), picco ritardato di circa tre ore.

    In ondata di calore il rialzo non è un'aggiunta costante per tutto il
    giorno, ma un picco stretto concentrato nel primo pomeriggio (~14-17):
    fuori da quella finestra il valore resta sulla stessa media/ampiezza di
    un giorno normale (16-22°C, comodamente smorzato rispetto al +13°C
    dell'aria), dentro sale abbastanza da superare la soglia di
    danno_radicale (35°C, Field et al. 2020, tetto ~40°C) — nelle due-tre
    ore centrali del picco l'aggiunta istantanea supera persino il boost
    costante dell'aria, coerente con l'esposizione diretta al sole di una
    superficie di terreno nudo, che può scaldarsi più rapidamente dell'aria
    circostante a mezzogiorno pur restando smorzata nella media giornaliera.

    Un rialzo esteso a tutto il giorno, come una prima versione di questa
    funzione, teneva il valore per gran parte della giornata a ridosso dei
    32°C — il confine superiore della banda di svernamento_oospore (Si
    Ammour et al., 2020) — rendendolo vulnerabile a oscillare avanti e
    indietro per il solo rumore di lettura, in una regola che non ha
    isteresi. Un picco stretto riduce quel tempo a ridosso del confine da
    circa 15 ore/giorno a circa 1.
    """
    ora = dt.hour + dt.minute / 60
    media, ampiezza = 19.0, 3.0
    valore = media + ampiezza * math.sin(2 * math.pi * (ora - 12) / 24)
    if scenario == "ondata_di_calore":
        picco = max(0.0, math.cos(2 * math.pi * (ora - 15) / 24)) ** 8
        valore += 19 * picco
    return round(valore + random.uniform(-0.3, 0.3), 1)


def raffreddamento_da_vento(velocita_vento: float) -> float:
    """Smart & Sinclair (1976): la temperatura della bacca scende di 5°C
    passando da 0,5 a 2,0 m/s di vento — l'unico intervallo verificato.
    Fuori da quell'intervallo il valore resta bloccato agli estremi,
    niente estrapolazione oltre il range testato.
    """
    v = max(0.5, min(2.0, velocita_vento))
    return (v - 0.5) * (5.0 / 1.5)


def genera_temp_bacca(temp_aria: float, dt: datetime, colore_bacca: str, scenario: str, velocita_vento: float) -> float:
    """Offset massimo 14°C (nero) / 10°C (bianco, Gambetta et al. 2021),
    ×1.3 in ondata di calore, raffreddato dal vento. Tetto teorico a vento
    nullo: ~60°C nero, ~55°C bianco — con la calibrazione di temp_aria
    tarata sulla soglia severa di RegolaOndataDiCalore (40°C), anche il
    bianco supera in teoria la soglia rapida di RegolaSunburn (15min).

    Col vento reale il quadro cambia parecchio: su un campione ampio di ore
    di picco in ondata di calore, il nero raggiunge la soglia rapida in una
    minoranza consistente dei casi, il bianco quasi mai — l'asimmetria
    resta pratica anche se non più assoluta come a vento nullo. Le soglie a
    esposizione più lunga (60/90min) restano ampiamente raggiungibili per
    entrambi i colori.

    Nota su temp_aria/temperatura_suolo: le due soglie a 35-40°C che ne
    hanno guidato la ricalibrazione (ondata_di_calore severo, danno_radicale)
    esistono oggi solo in regola_soglia, nessuna classe Java le valuta
    ancora. Non hanno lo stesso fondamento: danno_radicale ha una fonte
    diretta su temperatura del suolo (Field et al. 2020); la soglia severo
    di 40°C deriva da tessuto fogliare in laboratorio, non da aria misurata
    in campo, ed è dichiarata in schema come la più debole fra le soglie
    aggiunte finora — resa comunque raggiungibile qui perché la pubblicazione
    del dato deve precedere la sua eventuale implementazione, non seguirla.
    """
    ora = dt.hour + dt.minute / 60
    # sovratemperatura concentrata nelle ore di massima esposizione solare
    fattore_esposizione = max(0.0, math.sin(2 * math.pi * (ora - 9) / 24))
    offset_max = 14.0 if colore_bacca == "nero" else 10.0  # Gambetta et al., 2021
    offset = offset_max * fattore_esposizione
    if scenario == "ondata_di_calore":
        offset *= 1.3
    raffreddamento = raffreddamento_da_vento(velocita_vento)
    return round(temp_aria + offset - raffreddamento + random.uniform(-0.4, 0.4), 1)


def genera_letture_nodo(tipo_nodo: str, dt: datetime, stato: StatoParcella, colore_bacca: str,
                         temp_aria_riferimento: float, velocita_vento_riferimento: float):
    if tipo_nodo == "meteo":
        t = temp_aria_riferimento
        u = genera_umidita_aria(t, stato.scenario)
        b = genera_bagnatura_fogliare(dt, stato.pioggia_oggi_mm, u)
        return {
            "temperatura_aria": (t, "C"),
            "umidita_aria": (u, "%"),
            "pioggia": (stato.pioggia_oggi_mm, "mm"),  # totale cumulato del giorno, non un incremento: v. StatoParcella
            "bagnatura_fogliare": (b, "%"),
            "velocita_vento": (velocita_vento_riferimento, "m/s"),
        }
    if tipo_nodo == "idrico":
        rumore = round(random.uniform(-0.03, 0.03), 2)
        return {"psi_stem": (round(stato.psi_stem + rumore, 2), "MPa")}
    if tipo_nodo == "bacca":
        tb = genera_temp_bacca(temp_aria_riferimento, dt, colore_bacca, stato.scenario, velocita_vento_riferimento)
        return {"temperatura_bacca": (tb, "C")}
    if tipo_nodo == "suolo":
        ts = genera_temperatura_suolo(dt, stato.scenario)
        us = round(stato.umidita_suolo + random.uniform(-0.5, 0.5), 1)  # umidita_suolo: cumulato del giorno, v. StatoParcella
        return {
            "temperatura_suolo": (ts, "C"),
            "umidita_suolo": (us, "%"),
        }
    raise ValueError(f"Tipo nodo sconosciuto: {tipo_nodo}")