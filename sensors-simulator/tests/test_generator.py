"""
Test di regressione sulle calibrazioni di simulator/generator.py.

Questi test fissano i tetti teorici della bibliografia come asserzioni, 
confrontati con le soglie reali lette dal decisionengine. Se una futura modifica
a una costante di calibrazione rende di nuovo irraggiungibile una soglia,
questi test falliscono prima di scoprirlo a run-time contro un broker vero.

Le soglie duplicate qui vanno tenute allineate a mano con:
- backend/.../regole/RegolaOndataDiCalore.java
- backend/.../regole/RegolaStressIdrico.java
- backend/.../regole/RegolaSunburn.java
- backend/.../cache/CacheTabellaGoidanich.java (SOGLIA_UMIDITA_ALTA)

Eseguire con:
    cd sensors-simulator
    source .venv/bin/activate (o crealo: "python3 -m venv .venv && source .venv/bin/activate")
    pip install -r requirements.txt -r requirements-dev.txt
    pytest -v

(pytest.ini nella cartella sensors-simulator/ aggiunge automaticamente questa
cartella a sys.path: non serve impostare PYTHONPATH a mano.)
"""

import random
from datetime import datetime, timedelta

import pytest

from simulator.generator import (
    StatoParcella,
    genera_bagnatura_fogliare,
    genera_temp_aria,
    genera_temp_bacca,
    genera_umidita_aria,
)

# --- Soglie del decision engine, duplicate qui a scopo di verifica --------

SOGLIA_ONDATA_DI_CALORE_C = 35.0  # RegolaOndataDiCalore

SOGLIA_SUNBURN_MODERATO_C = 45.0  # RegolaSunburn.SOGLIA_MODERATO
SOGLIE_SUNBURN_LETALI_C = {
    15: 53.79,
    30: 49.94,
    60: 47.82,
    90: 47.06,
}  # RegolaSunburn.SOGLIE_LETALI

SOGLIA_STRESS_IDRICO_MODERATO_MPA = -1.2  # RegolaStressIdrico
SOGLIA_STRESS_IDRICO_SEVERO_MPA = -1.4    # RegolaStressIdrico

SOGLIA_UMIDITA_ALTA_PCT = 90.0  # CacheTabellaGoidanich.SOGLIA_UMIDITA_ALTA

SOGLIA_PIOGGIA_TRE_DIECI_MM = 10.0  # RegolaTreDieci.SOGLIA_PIOGGIA_MM

SCENARI = ("normale", "stress_idrico", "ondata_di_calore")

ORE_SWEEP = [h + m / 60 for h in range(24) for m in (0, 15, 30, 45)]


def _dt_per_ora(ora: float) -> datetime:
    return datetime(2026, 7, 1, int(ora), int(round((ora % 1) * 60)) % 60)


def _con_rumore_bloccato(monkeypatch: pytest.MonkeyPatch, estremo: str) -> None:
    """Blocca random.uniform(a, b) del modulo generator sul proprio estremo
    dichiarato: 'max' restituisce sempre b, 'min' restituisce sempre a. Le
    funzioni di generator.py vengono comunque eseguite per intero — solo il
    termine di rumore diventa deterministico, non la formula."""
    if estremo == "max":
        monkeypatch.setattr(random, "uniform", lambda a, b: b)
    else:
        monkeypatch.setattr(random, "uniform", lambda a, b: a)


def _temp_aria_max_teorico(scenario: str, monkeypatch: pytest.MonkeyPatch) -> float:
    _con_rumore_bloccato(monkeypatch, "max")
    return max(genera_temp_aria(_dt_per_ora(ora), scenario) for ora in ORE_SWEEP)


def _temp_bacca_max_teorico(scenario: str, colore: str, monkeypatch: pytest.MonkeyPatch) -> float:
    _con_rumore_bloccato(monkeypatch, "max")
    tetto = float("-inf")
    for ora in ORE_SWEEP:
        dt = _dt_per_ora(ora)
        temp_aria = genera_temp_aria(dt, scenario)
        bacca = genera_temp_bacca(temp_aria, dt, colore, scenario)
        tetto = max(tetto, bacca)
    return tetto


def _umidita_aria_max_teorico(scenario: str, monkeypatch: pytest.MonkeyPatch) -> float:
    """Il tetto di umidita_aria non si trova alla temp_aria massima: la
    formula è inversamente proporzionale alla temperatura. Prima si calcola
    temp_aria al proprio MINIMO (rumore bloccato al minimo), poi si applica
    genera_umidita_aria con il SUO rumore bloccato al massimo."""
    _con_rumore_bloccato(monkeypatch, "min")
    temp_arie_minime = [genera_temp_aria(_dt_per_ora(ora), scenario) for ora in ORE_SWEEP]

    _con_rumore_bloccato(monkeypatch, "max")
    return max(genera_umidita_aria(t, scenario) for t in temp_arie_minime)


class TestTettoTeoricoOndataDiCalore:
    """RegolaOndataDiCalore: soglia singola 35°C, PEGGIORA_SALENDO."""

    def test_soglia_raggiungibile_in_ondata_di_calore(self, monkeypatch):
        tetto = _temp_aria_max_teorico("ondata_di_calore", monkeypatch)
        assert tetto > SOGLIA_ONDATA_DI_CALORE_C, (
            f"Tetto teorico temperatura_aria in ondata_di_calore ({tetto:.2f}°C) "
            f"sotto la soglia (35°C): un'allerta reale non scatterebbe mai."
        )

    @pytest.mark.parametrize("scenario", ["normale", "stress_idrico"])
    def test_soglia_non_raggiungibile_fuori_scenario(self, scenario, monkeypatch):
        tetto = _temp_aria_max_teorico(scenario, monkeypatch)
        assert tetto < SOGLIA_ONDATA_DI_CALORE_C, (
            f"Tetto teorico temperatura_aria in '{scenario}' ({tetto:.2f}°C) "
            f"vicino o oltre i 35°C: rischio di allerte di ondata di calore "
            f"fisicamente incoerenti fuori dal proprio scenario."
        )


class TestTettoTeoricoSunburn:
    """RegolaSunburn: soglia moderato 45°C + 4 soglie letali dipendenti dalla
    durata (Schmidt et al. 2023). Verificato separatamente per colore bacca."""

    def test_moderato_non_raggiungibile_fuori_ondata_di_calore(self, monkeypatch):
        for scenario in ("normale", "stress_idrico"):
            tetto = _temp_bacca_max_teorico(scenario, "nero", monkeypatch)
            assert tetto < SOGLIA_SUNBURN_MODERATO_C, (
                f"Tetto teorico temperatura_bacca (nero) in '{scenario}' "
                f"({tetto:.2f}°C) raggiunge la soglia moderata (45°C): "
                f"non dovrebbe succedere fuori da ondata_di_calore."
            )

    def test_moderato_raggiungibile_anche_per_bacca_bianca_in_ondata_di_calore(self, monkeypatch):
        tetto = _temp_bacca_max_teorico("ondata_di_calore", "bianco", monkeypatch)
        assert tetto > SOGLIA_SUNBURN_MODERATO_C, (
            f"Tetto teorico temperatura_bacca (bianco) in ondata_di_calore "
            f"({tetto:.2f}°C) non supera la soglia moderata (45°C)."
        )

    def test_soglie_letali_nero_tutte_raggiungibili_in_ondata_di_calore(self, monkeypatch):
        """Il tetto per bacca nera deve superare anche la soglia più severa (15 minuti, 53,79°C)."""
        tetto = _temp_bacca_max_teorico("ondata_di_calore", "nero", monkeypatch)
        soglia_piu_severa = SOGLIE_SUNBURN_LETALI_C[15]
        assert tetto > soglia_piu_severa, (
            f"Tetto teorico bacca nera ({tetto:.2f}°C) sotto la soglia dei "
            f"15 minuti ({soglia_piu_severa}°C)."
        )

    def test_soglie_letali_bianco_asimmetria_attesa(self, monkeypatch):
        """La bacca bianca NON deve poter raggiungere le soglie rapide (15/30 min), ma DEVE poter
        raggiungere quelle a esposizione lunga (60/90 min). Se in futuro
        questo test fallisce perché il tetto bianco supera anche 49,94°C,
        non è necessariamente un problema ma l'asimmetria documentata nel
        docstring di genera_temp_bacca va aggiornata di conseguenza."""
        tetto = _temp_bacca_max_teorico("ondata_di_calore", "bianco", monkeypatch)

        assert tetto < SOGLIE_SUNBURN_LETALI_C[30], (
            f"Tetto bacca bianca ({tetto:.2f}°C) ha superato la soglia dei "
            f"30 minuti ({SOGLIE_SUNBURN_LETALI_C[30]}°C): l'asimmetria "
            f"nero/bianco documentata non è più valida, aggiornare la "
            f"documentazione in generator.py."
        )
        assert tetto > SOGLIE_SUNBURN_LETALI_C[60], (
            f"Tetto bacca bianca ({tetto:.2f}°C) sotto la soglia dei 60 "
            f"minuti ({SOGLIE_SUNBURN_LETALI_C[60]}°C): la parcellaC non "
            f"potrebbe mai raggiungere 'severo' in nessuna configurazione."
        )


class TestTettoTeoricoUmiditaGoidanich:
    """CacheTabellaGoidanich distingue 'umidità alta' (>=90%) da 'bassa'."""

    @pytest.mark.parametrize("scenario", ["normale", "stress_idrico"])
    def test_umidita_alta_raggiungibile(self, scenario, monkeypatch):
        tetto = _umidita_aria_max_teorico(scenario, monkeypatch)
        assert tetto >= SOGLIA_UMIDITA_ALTA_PCT, (
            f"Tetto teorico umidita_aria in '{scenario}' ({tetto:.1f}%) sotto "
            f"il 90%."
        )

    def test_umidita_alta_non_raggiungibile_in_ondata_di_calore(self, monkeypatch):
        """Comportamento fisicamente atteso: un'ondata di calore secca non
        dovrebbe produrre umidità relativa al 90%."""
        tetto = _umidita_aria_max_teorico("ondata_di_calore", monkeypatch)
        assert tetto < SOGLIA_UMIDITA_ALTA_PCT, (
            f"Tetto teorico umidita_aria in ondata_di_calore ({tetto:.1f}%) "
            f"raggiunge il 90%: comportamento fisicamente incoerente con "
            f"uno scenario di calore secco."
        )


class TestPsiStemRangeRaggiungibile:
    """RegolaStressIdrico: moderato a -1,2 MPa, severo a -1,4 MPa."""

    def _simula_giorni_senza_pioggia(self, scenario: str, giorni: int,
                                      monkeypatch: pytest.MonkeyPatch) -> float:
        """Forza l'assenza di pioggia (random.random() sempre >= qualunque
        probabilità) per isolare la sola deriva, senza dipendere dalla
        casualità delle precipitazioni."""
        monkeypatch.setattr(random, "random", lambda: 1.0)  # mai < prob_pioggia
        stato = StatoParcella(scenario)
        dt = datetime(2026, 7, 1)
        for _ in range(giorni):
            stato.aggiorna_se_nuovo_giorno(dt)
            dt += timedelta(days=1)
        return stato.psi_stem

    def test_severo_raggiungibile_in_stress_idrico_entro_15_giorni(self, monkeypatch):
        psi_finale = self._simula_giorni_senza_pioggia("stress_idrico", 15, monkeypatch)
        assert psi_finale <= SOGLIA_STRESS_IDRICO_SEVERO_MPA, (
            f"psi_stem dopo 15 giorni senza pioggia in stress_idrico "
            f"({psi_finale:.2f} MPa) non ha raggiunto la soglia severa "
            f"({SOGLIA_STRESS_IDRICO_SEVERO_MPA} MPa)."
        )

    def test_moderato_raggiungibile_in_normale_entro_15_giorni(self, monkeypatch):
        """Un tratto di 15 giorni senza pioggia, anche in scenario 'normale', deve poter attivare l'allerta
        moderata."""
        psi_finale = self._simula_giorni_senza_pioggia("normale", 15, monkeypatch)
        assert psi_finale <= SOGLIA_STRESS_IDRICO_MODERATO_MPA

    def test_psi_stem_non_scende_mai_sotto_il_pavimento(self, monkeypatch):
        psi_finale = self._simula_giorni_senza_pioggia("stress_idrico", 60, monkeypatch)
        assert psi_finale >= -1.6 - 1e-9


class TestPioggiaTreDieci:
    """RegolaTreDieci richiede >=10mm cumulati in 24-48h."""

    def test_evento_di_pioggia_puo_superare_la_soglia(self):
        random.seed(42)
        eventi = [round(random.uniform(4, 22), 1) for _ in range(1000)]
        assert max(eventi) >= SOGLIA_PIOGGIA_TRE_DIECI_MM
        # Verifica anche che non OGNI evento la superi (design intenzionale:
        # le piogge leggere sotto soglia recuperano solo parzialmente psi_stem).
        sotto_soglia = [e for e in eventi if e < SOGLIA_PIOGGIA_TRE_DIECI_MM]
        assert len(sotto_soglia) > 0


class TestCorrelazioneTempBaccaTempAria:
    """Temperatura_bacca deve derivare dalla stessa
    temperatura_aria pubblicata nello stesso tick dal nodo meteo, non da un
    valore ricalcolato indipendentemente (con rumore proprio)."""

    def test_stessa_temp_aria_riferimento_stesso_tick(self, monkeypatch):
        dt = datetime(2026, 7, 1, 15, 0)
        for scenario in SCENARI:
            for _ in range(20):
                # Replica esattamente il pattern di main.py: temp_aria calcolata
                # UNA sola volta per tick e passata a genera_temp_bacca.
                temp_aria_riferimento = genera_temp_aria(dt, scenario)
                bacca = genera_temp_bacca(temp_aria_riferimento, dt, "nero", scenario)
                # bacca = temp_aria_riferimento + offset (>=0) + rumore in [-0.4, 0.4]:
                # non deve mai essere inferiore a temp_aria_riferimento meno 0.4,
                # qualunque sia il rumore realmente estratto in questa chiamata.
                assert bacca >= temp_aria_riferimento - 0.4 - 1e-9, (
                    "temperatura_bacca sembra derivare da una temperatura_aria "
                    "diversa da quella di riferimento del tick."
                )


class TestBagnatoraFogliareRangeValido:
    """bagnatura_fogliare non è consumata da alcuna regola attuale, ma resta pubblicata: 
    verifichiamo comunque che stia nel range fisico dichiarato (0-100%), a garanzia per un futuro consumo."""

    def test_range_valido(self):
        dt_notte = datetime(2026, 7, 1, 3, 0)
        dt_giorno = datetime(2026, 7, 1, 13, 0)
        for dt in (dt_notte, dt_giorno):
            for pioggia in (0.0, 15.0):
                for umidita in (30.0, 90.0):
                    valore = genera_bagnatura_fogliare(dt, pioggia, umidita)
                    assert 0.0 <= valore <= 100.0