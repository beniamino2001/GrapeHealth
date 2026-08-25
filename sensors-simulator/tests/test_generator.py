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
    pip install -r requirements.txt
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
    genera_letture_nodo,
    genera_temp_aria,
    genera_temp_bacca,
    genera_temperatura_suolo,
    genera_umidita_aria,
    genera_velocita_vento,
    raffreddamento_da_vento,
)

# --- Soglie del decision engine, duplicate qui a scopo di verifica --------

SOGLIA_ONDATA_DI_CALORE_C = 35.0  # RegolaOndataDiCalore, moderato
SOGLIA_ONDATA_DI_CALORE_SEVERO_C = 40.0  # RegolaOndataDiCalore, severo (Luo et al. 2011)
SOGLIA_DANNO_RADICALE_C = 35.0  # temperatura_suolo, severo (Field et al. 2020)

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


def _temp_suolo_max_teorico(scenario: str, monkeypatch: pytest.MonkeyPatch) -> float:
    _con_rumore_bloccato(monkeypatch, "max")
    return max(genera_temperatura_suolo(_dt_per_ora(ora), scenario) for ora in ORE_SWEEP)


def _temp_bacca_max_teorico(scenario: str, colore: str, monkeypatch: pytest.MonkeyPatch, velocita_vento: float = 0.0) -> float:
    """Tetto teorico a un vento scelto esplicitamente (default 0.0 m/s, che
    per il clamp di raffreddamento_da_vento equivale a "nessun raffreddamento",
    lo stesso tetto già verificato prima di cablare il vento). genera_temp_bacca
    non genera più il proprio vento internamente: passarlo da qui invece di
    simularlo con monkeypatch è più diretto e altrettanto legato al codice
    reale, dato che raffreddamento_da_vento() viene comunque eseguita per intero."""
    _con_rumore_bloccato(monkeypatch, "max")
    tetto = float("-inf")
    for ora in ORE_SWEEP:
        dt = _dt_per_ora(ora)
        temp_aria = genera_temp_aria(dt, scenario)
        bacca = genera_temp_bacca(temp_aria, dt, colore, scenario, velocita_vento)
        tetto = max(tetto, bacca)
    return tetto


def _temp_bacca_max_teorico_a_vento_realistico(scenario: str, colore: str, monkeypatch: pytest.MonkeyPatch) -> float:
    """Come sopra, ma il vento non è fissato: viene generato per davvero da
    genera_velocita_vento(), con lo stesso rumore bloccato al massimo — che
    per il vento significa il caso PIÙ SFAVOREVOLE per temp_bacca (più vento,
    più raffreddamento), non il più favorevole. Usato per verificare quanto
    spesso una soglia resta raggiungibile in condizioni realistiche, non solo
    nel caso limite teorico a vento nullo."""
    _con_rumore_bloccato(monkeypatch, "max")
    tetto = float("-inf")
    for ora in ORE_SWEEP:
        dt = _dt_per_ora(ora)
        temp_aria = genera_temp_aria(dt, scenario)
        vento = genera_velocita_vento(dt, scenario)
        bacca = genera_temp_bacca(temp_aria, dt, colore, scenario, vento)
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
    """RegolaOndataDiCalore: moderato >35°C, severo >=40°C."""

    def test_soglia_raggiungibile_in_ondata_di_calore(self, monkeypatch):
        tetto = _temp_aria_max_teorico("ondata_di_calore", monkeypatch)
        assert tetto > SOGLIA_ONDATA_DI_CALORE_C, (
            f"Tetto teorico temperatura_aria in ondata_di_calore ({tetto:.2f}°C) "
            f"sotto la soglia (35°C): un'allerta reale non scatterebbe mai."
        )

    def test_soglia_severo_raggiungibile_in_ondata_di_calore(self, monkeypatch):
        tetto = _temp_aria_max_teorico("ondata_di_calore", monkeypatch)
        assert tetto > SOGLIA_ONDATA_DI_CALORE_SEVERO_C, (
            f"Tetto teorico temperatura_aria in ondata_di_calore ({tetto:.2f}°C) "
            f"sotto la soglia severa (40°C): il livello più grave della regola "
            f"non scatterebbe mai."
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

    def test_tetto_a_vento_nullo_nero_e_bianco(self, monkeypatch):
        """Tetto teorico a vento nullo (raffreddamento zero per clamp), per
        entrambi i colori: ~60°C nero, ~55°C bianco con la calibrazione di
        temp_aria tarata sulla soglia severa di RegolaOndataDiCalore (40°C).
        A questo tetto anche il bianco supera la soglia dei 15 minuti
        (53,79°C) — la Monte Carlo qui sotto mostra che con vento reale
        questo non succede quasi mai in pratica."""
        tetto_nero = _temp_bacca_max_teorico("ondata_di_calore", "nero", monkeypatch, velocita_vento=0.0)
        tetto_bianco = _temp_bacca_max_teorico("ondata_di_calore", "bianco", monkeypatch, velocita_vento=0.0)
        assert tetto_nero == pytest.approx(60.1, abs=0.05)
        assert tetto_bianco == pytest.approx(54.9, abs=0.05)

    def test_asimmetria_nero_bianco_con_vento_reale(self):
        """Verifica statistica, non il caso limite teorico: su un campione
        ampio di ore di picco (11-17) in ondata di calore, con vento
        generato per davvero (nessun rumore forzato), il nero raggiunge la
        soglia dei 15 minuti in una quota consistente dei casi, il bianco
        quasi mai — l'asimmetria resta pratica anche se il tetto teorico a
        vento nullo non la garantisce più in assoluto."""
        N = 5000
        soglia_15min = SOGLIE_SUNBURN_LETALI_C[15]
        raggiunti = {"nero": 0, "bianco": 0}
        for _ in range(N):
            ora = random.uniform(11, 17)
            dt = datetime(2026, 7, 1, int(ora), int((ora % 1) * 60))
            temp_aria = genera_temp_aria(dt, "ondata_di_calore")
            vento = genera_velocita_vento(dt, "ondata_di_calore")
            for colore in ("nero", "bianco"):
                bacca = genera_temp_bacca(temp_aria, dt, colore, "ondata_di_calore", vento)
                if bacca >= soglia_15min:
                    raggiunti[colore] += 1

        quota_nero = raggiunti["nero"] / N
        quota_bianco = raggiunti["bianco"] / N
        assert quota_nero > 0.20, (
            f"Solo {quota_nero:.1%} dei casi raggiunge la soglia dei 15 minuti per il nero: "
            f"atteso una quota consistente (>20%), il vento starebbe raffreddando troppo."
        )
        assert quota_bianco < 0.05, (
            f"{quota_bianco:.1%} dei casi raggiunge la soglia dei 15 minuti per il bianco: "
            f"atteso restasse raro (<5%), l'asimmetria pratica nero/bianco starebbe svanendo."
        )

    def test_soglia_a_60_minuti_resta_ampiamente_raggiungibile_per_entrambi_i_colori(self, monkeypatch):
        """Le soglie a esposizione più lunga devono restare raggiungibili per
        entrambi i colori anche col raffreddamento da vento al suo massimo
        (caso peggiore per la temperatura): altrimenti la regola sarebbe
        irraggiungibile per il bianco anche nel suo livello meno estremo."""
        for colore in ("nero", "bianco"):
            tetto = _temp_bacca_max_teorico_a_vento_realistico("ondata_di_calore", colore, monkeypatch)
            assert tetto > SOGLIE_SUNBURN_LETALI_C[60], (
                f"Tetto bacca {colore} a vento al massimo ({tetto:.2f}°C) sotto la "
                f"soglia dei 60 minuti: il raffreddamento da vento renderebbe "
                f"irraggiungibile anche la soglia meno estrema."
            )


class TestRaffreddamentoDaVento:
    """Smart & Sinclair (1976): -5°C sulla FST passando da 0,5 a 2,0 m/s,
    intervallo testato oltre il quale il valore va bloccato agli estremi."""

    def test_nessun_raffreddamento_a_vento_al_minimo_testato(self):
        assert raffreddamento_da_vento(0.5) == pytest.approx(0.0)

    def test_nessun_raffreddamento_sotto_il_minimo_testato(self):
        """Un vento più calmo del minimo testato non deve raffreddare più di
        quanto faccia il minimo stesso: niente estrapolazione oltre il range
        verificato in bibliografia."""
        assert raffreddamento_da_vento(0.0) == pytest.approx(0.0)
        assert raffreddamento_da_vento(0.2) == pytest.approx(0.0)

    def test_raffreddamento_massimo_al_limite_superiore_testato(self):
        assert raffreddamento_da_vento(2.0) == pytest.approx(5.0)

    def test_raffreddamento_non_supera_il_massimo_oltre_il_range_testato(self):
        """Un vento più forte del massimo testato non deve raffreddare più
        dei 5°C già misurati al limite superiore: niente estrapolazione."""
        assert raffreddamento_da_vento(5.0) == pytest.approx(5.0)
        assert raffreddamento_da_vento(20.0) == pytest.approx(5.0)

    def test_raffreddamento_lineare_a_meta_intervallo(self):
        # 1,25 m/s è il punto medio fra 0,5 e 2,0: raffreddamento atteso 2,5°C
        assert raffreddamento_da_vento(1.25) == pytest.approx(2.5, abs=0.01)


class TestCorrelazioneVentoTempBacca:
    """Lo stesso principio già verificato per temperatura_aria/temperatura_bacca
    (§10.5): il raffreddamento da vento in temperatura_bacca deve riflettere
    lo stesso vento pubblicato dal nodo meteo nello stesso tick, non un
    secondo campione indipendente."""

    def test_stesso_vento_riferimento_produce_lo_stesso_raffreddamento(self, monkeypatch):
        dt = datetime(2026, 7, 1, 15, 0)
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)  # rumore azzerato, per un confronto esatto
        for vento_riferimento in (0.5, 1.0, 1.5, 2.0):
            temp_aria = genera_temp_aria(dt, "ondata_di_calore")
            bacca = genera_temp_bacca(temp_aria, dt, "nero", "ondata_di_calore", vento_riferimento)
            raffreddamento_atteso = raffreddamento_da_vento(vento_riferimento)
            offset_atteso = 14.0 * 1.0 * 1.3  # fattore_esposizione=1 alle 15:00, nero, ondata di calore
            assert bacca == pytest.approx(temp_aria + offset_atteso - raffreddamento_atteso, abs=0.06), (
                f"Con vento_riferimento={vento_riferimento} il raffreddamento applicato "
                f"non corrisponde a quello atteso: temperatura_bacca non sta usando lo "
                f"stesso valore di vento passato dal chiamante."
            )

    def test_piu_vento_produce_temperatura_bacca_piu_bassa(self, monkeypatch):
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        dt = datetime(2026, 7, 1, 15, 0)
        temp_aria = genera_temp_aria(dt, "ondata_di_calore")
        bacca_poco_vento = genera_temp_bacca(temp_aria, dt, "nero", "ondata_di_calore", 0.5)
        bacca_molto_vento = genera_temp_bacca(temp_aria, dt, "nero", "ondata_di_calore", 2.0)
        assert bacca_molto_vento < bacca_poco_vento


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
                bacca = genera_temp_bacca(temp_aria_riferimento, dt, "nero", scenario, velocita_vento=0.5)
                # A vento minimo (0.5 m/s, raffreddamento zero per clamp):
                # bacca = temp_aria_riferimento + offset (>=0) + rumore in [-0.4, 0.4],
                # non deve mai essere inferiore a temp_aria_riferimento meno 0.4.
                assert bacca >= temp_aria_riferimento - 0.4 - 1e-9, (
                    "temperatura_bacca sembra derivare da una temperatura_aria "
                    "diversa da quella di riferimento del tick."
                )


class TestAggiornaSeNuovoGiornoRamiPioggia:
    """I tre rami di aggiorna_se_nuovo_giorno per il giorno in cui piove
    (pioggia abbondante, pioggia leggera, nessuna pioggia) erano esercitati
    solo indirettamente dai test di raggiungibilità delle soglie, che
    forzano sempre l'assenza di pioggia per isolare la sola deriva."""

    def test_stesso_giorno_non_rilancia_la_dinamica_del_giorno(self, monkeypatch):
        """Una seconda chiamata nello stesso giorno solare non deve
        ripetere l'estrazione della pioggia: altrimenti una parcella
        letta più volte nello stesso giorno avrebbe pioggia diversa a
        ogni lettura, il che non ha senso fisico."""
        chiamate = {"n": 0}

        def random_che_conta(*args, **kwargs):
            chiamate["n"] += 1
            return 0.0  # farebbe sempre scattare pioggia, se richiamato

        monkeypatch.setattr(random, "random", random_che_conta)
        stato = StatoParcella("normale")
        dt = datetime(2026, 7, 1, 8, 0)

        stato.aggiorna_se_nuovo_giorno(dt)
        chiamate_dopo_la_prima = chiamate["n"]
        stato.aggiorna_se_nuovo_giorno(dt.replace(hour=14))  # stesso giorno solare, ora diversa

        assert chiamate["n"] == chiamate_dopo_la_prima, (
            "aggiorna_se_nuovo_giorno ha richiamato random.random() anche "
            "per una seconda lettura nello stesso giorno solare."
        )

    def test_pioggia_abbondante_recupera_psi_stem_e_umidita_suolo(self, monkeypatch):
        monkeypatch.setattr(random, "random", lambda: 0.0)      # fa sempre piovere
        monkeypatch.setattr(random, "uniform", lambda a, b: b)  # estremo massimo di ogni intervallo
        stato = StatoParcella("normale")
        stato.psi_stem = -1.2

        stato.aggiorna_se_nuovo_giorno(datetime(2026, 7, 1))

        assert stato.pioggia_oggi_mm >= 10, "Con random.uniform forzato al massimo (22) doveva scattare il ramo abbondante."
        assert stato.psi_stem > -1.2, "Un evento di pioggia abbondante deve far recuperare psi_stem, non peggiorarlo."
        assert stato.umidita_suolo > 25.0, "Un evento di pioggia abbondante deve far recuperare anche umidita_suolo."

    def test_pioggia_abbondante_non_supera_mai_il_tetto_massimo(self, monkeypatch):
        monkeypatch.setattr(random, "random", lambda: 0.0)
        monkeypatch.setattr(random, "uniform", lambda a, b: b)
        stato = StatoParcella("normale")
        stato.psi_stem = -0.76  # già quasi al tetto

        stato.aggiorna_se_nuovo_giorno(datetime(2026, 7, 1))

        assert stato.psi_stem <= -0.75 + 1e-9

    def test_pioggia_leggera_recupera_solo_parzialmente(self, monkeypatch):
        """Sequenza di random.uniform: prima chiamata decide i mm di pioggia
        (forzati sotto 10), seconda per il recupero parziale di umidita_suolo."""
        valori_uniform = iter([5.0, 2.0])  # 5mm di pioggia; +2.0 di umidita_suolo

        def uniform_sequenziale(a, b):
            return next(valori_uniform)

        monkeypatch.setattr(random, "random", lambda: 0.0)
        monkeypatch.setattr(random, "uniform", uniform_sequenziale)
        stato = StatoParcella("normale")
        stato.psi_stem = -1.2

        stato.aggiorna_se_nuovo_giorno(datetime(2026, 7, 1))

        assert stato.pioggia_oggi_mm == 5.0
        assert stato.umidita_suolo == pytest.approx(27.0, abs=1e-9), (
            "Pioggia leggera deve far recuperare anche umidita_suolo, non solo psi_stem."
        )
        assert stato.psi_stem == pytest.approx(-1.1, abs=1e-9), (
            "Pioggia leggera (<10mm) deve recuperare psi_stem di soli 0.1 MPa, "
            "non quanto un evento abbondante."
        )

    def test_nessuna_pioggia_fa_derivare_psi_stem_e_umidita_suolo(self, monkeypatch):
        monkeypatch.setattr(random, "random", lambda: 1.0)  # non piove mai
        stato = StatoParcella("stress_idrico")
        stato.psi_stem = -1.0

        stato.aggiorna_se_nuovo_giorno(datetime(2026, 7, 1))

        assert stato.pioggia_oggi_mm == 0.0
        assert stato.psi_stem == pytest.approx(-1.06, abs=1e-9)  # deriva -0.06 in stress_idrico
        assert stato.umidita_suolo == pytest.approx(25.0 - 0.8, abs=1e-9)  # deriva -0.8 in stress_idrico

    def test_umidita_suolo_non_scende_mai_sotto_il_pavimento(self, monkeypatch):
        monkeypatch.setattr(random, "random", lambda: 1.0)  # non piove mai
        stato = StatoParcella("stress_idrico")
        stato.umidita_suolo = 10.3  # già quasi al pavimento

        stato.aggiorna_se_nuovo_giorno(datetime(2026, 7, 1))

        assert stato.umidita_suolo >= 10.0 - 1e-9


class TestGeneraLettureNodo:
    """Il dispatcher per tipo di nodo: nessun test lo chiamava direttamente
    finora, solo le funzioni genera_* che chiama a sua volta."""

    def _stato(self, scenario="normale", psi_stem=-0.95, pioggia_oggi_mm=3.0):
        stato = StatoParcella(scenario)
        stato.psi_stem = psi_stem
        stato.pioggia_oggi_mm = pioggia_oggi_mm
        return stato

    def test_meteo_restituisce_i_cinque_parametri_con_unita_corrette(self):
        letture = genera_letture_nodo(
            tipo_nodo="meteo", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(), colore_bacca="nero", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=1.2,
        )

        assert set(letture.keys()) == {
            "temperatura_aria", "umidita_aria", "pioggia", "bagnatura_fogliare",
            "velocita_vento",
        }
        valore_temp, unita_temp = letture["temperatura_aria"]
        assert valore_temp == 28.5, "Il nodo meteo deve ripubblicare esattamente temp_aria_riferimento, non ricalcolarla."
        assert unita_temp == "C"
        assert letture["umidita_aria"][1] == "%"
        assert letture["pioggia"] == (3.0, "mm")
        assert letture["bagnatura_fogliare"][1] == "%"
        assert letture["velocita_vento"] == (1.2, "m/s"), (
            "Il nodo meteo deve ripubblicare esattamente velocita_vento_riferimento, non ricalcolarla."
        )

    def test_suolo_restituisce_temperatura_e_umidita_del_suolo(self):
        letture = genera_letture_nodo(
            tipo_nodo="suolo", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(), colore_bacca="nero", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=1.2,
        )

        assert set(letture.keys()) == {"temperatura_suolo", "umidita_suolo"}
        assert letture["temperatura_suolo"][1] == "C"
        assert letture["umidita_suolo"][1] == "%"

    def test_idrico_restituisce_solo_psi_stem_in_mpa(self):
        letture = genera_letture_nodo(
            tipo_nodo="idrico", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(psi_stem=-1.1), colore_bacca="nero", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=1.2,
        )

        assert set(letture.keys()) == {"psi_stem"}
        valore, unita = letture["psi_stem"]
        assert unita == "MPa"
        assert abs(valore - (-1.1)) <= 0.03 + 1e-9, "psi_stem pubblicato si discosta più del rumore dichiarato (±0.03 MPa)."

    def test_bacca_restituisce_solo_temperatura_bacca_in_c(self):
        letture = genera_letture_nodo(
            tipo_nodo="bacca", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(), colore_bacca="bianco", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=1.2,
        )

        assert set(letture.keys()) == {"temperatura_bacca"}
        assert letture["temperatura_bacca"][1] == "C"

    def test_bacca_usa_lo_stesso_vento_riferimento_del_nodo_meteo(self):
        """Lo stesso principio già verificato per temp_aria_riferimento: il
        vento usato per il raffreddamento di temperatura_bacca deve essere
        quello passato da main.py per questo tick, non un secondo campione."""
        letture_poco_vento = genera_letture_nodo(
            tipo_nodo="bacca", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(), colore_bacca="nero", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=0.5,
        )
        letture_molto_vento = genera_letture_nodo(
            tipo_nodo="bacca", dt=datetime(2026, 7, 1, 15, 0),
            stato=self._stato(), colore_bacca="nero", temp_aria_riferimento=28.5,
            velocita_vento_riferimento=2.0,
        )
        assert letture_molto_vento["temperatura_bacca"][0] < letture_poco_vento["temperatura_bacca"][0]

    def test_tipo_nodo_sconosciuto_solleva_value_error(self):
        with pytest.raises(ValueError, match="Tipo nodo sconosciuto"):
            genera_letture_nodo(
                tipo_nodo="chioma", dt=datetime(2026, 7, 1, 15, 0),
                stato=self._stato(), colore_bacca="nero", temp_aria_riferimento=28.5,
                velocita_vento_riferimento=1.2,
            )


class TestStatoParcellaRoundTrip:
    """esporta_stato()/ripristina_stato(): la continuità di sessione fra due
    esecuzioni dipende interamente dal fatto che siano davvero inverse."""

    def test_esporta_poi_ripristina_riproduce_lo_stesso_stato(self):
        originale = StatoParcella("stress_idrico")
        originale.psi_stem = -1.25
        originale.pioggia_oggi_mm = 0.0
        originale.umidita_suolo = 18.5
        originale._ultimo_giorno = "2026-07-10"

        nuovo = StatoParcella("stress_idrico")
        nuovo.ripristina_stato(originale.esporta_stato())

        assert nuovo.psi_stem == originale.psi_stem
        assert nuovo.pioggia_oggi_mm == originale.pioggia_oggi_mm
        assert nuovo.umidita_suolo == originale.umidita_suolo
        assert nuovo._ultimo_giorno == originale._ultimo_giorno

    def test_ripristina_stato_non_tocca_lo_scenario(self):
        """Lo scenario appartiene alla sessione corrente, non allo stato
        salvato: cambiare scenario da un avvio all'altro deve restare
        possibile pur riprendendo la stessa linea temporale."""
        stato_salvato = StatoParcella("normale")
        stato_salvato.psi_stem = -1.3

        ripreso_con_altro_scenario = StatoParcella("ondata_di_calore")
        ripreso_con_altro_scenario.ripristina_stato(stato_salvato.esporta_stato())

        assert ripreso_con_altro_scenario.scenario == "ondata_di_calore"
        assert ripreso_con_altro_scenario.psi_stem == -1.3

    def test_ripristina_stato_su_dati_parziali_mantiene_i_default_per_i_campi_assenti(self):
        """Un dizionario di stato con solo alcuni campi (es. da una versione
        precedente del formato) non deve azzerare gli altri."""
        stato = StatoParcella("normale")
        stato.ripristina_stato({"psi_stem": -1.05})

        assert stato.psi_stem == -1.05
        assert stato.umidita_suolo == 25.0  # default costruttore, non sovrascritto


class TestBagnatoraFogliareRangeValido:
    """Range fisico dichiarato (0-100%): la generazione non dipende da quale
    regola la consumi, ma deve restare plausibile in ogni caso."""

    def test_range_valido(self):
        dt_notte = datetime(2026, 7, 1, 3, 0)
        dt_giorno = datetime(2026, 7, 1, 13, 0)
        for dt in (dt_notte, dt_giorno):
            for pioggia in (0.0, 15.0):
                for umidita in (30.0, 90.0):
                    valore = genera_bagnatura_fogliare(dt, pioggia, umidita)
                    assert 0.0 <= valore <= 100.0

    def test_pioggia_in_corso_produce_bagnatura_alta_indipendentemente_dall_ora(self, monkeypatch):
        """Con pioggia > 0 il ramo notte/giorno non deve nemmeno essere
        considerato: la sola presenza di pioggia deve dominare."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)  # rumore azzerato
        for ora in (3, 13, 21):
            valore = genera_bagnatura_fogliare(datetime(2026, 7, 1, ora, 0), pioggia_oggi_mm=5.0, umidita_aria=50.0)
            assert valore == pytest.approx(90.0, abs=0.1)

    def test_notte_senza_pioggia_dipende_dall_umidita(self, monkeypatch):
        """Verifica il valore atteso del ramo notturno (ora<=8 o ora>=21),
        non solo che ricada nel range 0-100: un errore che scambiasse questo
        ramo con quello diurno passerebbe inosservato con il solo controllo
        di range."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        valore = genera_bagnatura_fogliare(datetime(2026, 7, 1, 3, 0), pioggia_oggi_mm=0.0, umidita_aria=90.0)
        # base = 40 + (90-60)*0.5 = 55
        assert valore == pytest.approx(55.0, abs=0.1)

    def test_ore_centrali_senza_pioggia_si_asciuga_col_passare_delle_ore(self, monkeypatch):
        """Verifica il valore atteso del ramo diurno (decrescente dalle 8 in
        poi), non solo il range."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        valore_alle_9 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 9, 0), 0.0, 50.0)
        valore_alle_15 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 15, 0), 0.0, 50.0)
        # base(9) = 20-(9-8)*3 = 17; base(15) = max(0, 20-(15-8)*3) = max(0,-1) = 0
        assert valore_alle_9 == pytest.approx(17.0, abs=0.1)
        assert valore_alle_15 == pytest.approx(0.0, abs=0.1)
        assert valore_alle_15 < valore_alle_9, "La bagnatura fogliare dovrebbe diminuire avvicinandosi a metà giornata."

    def test_transizione_continua_entro_l_ora_non_a_scalini(self, monkeypatch):
        """Un processo fisico continuo (l'asciugatura della foglia) non deve
        saltare bruscamente al cambio d'ora: usare dt.hour da solo, ignorando
        i minuti, produrrebbe un salto secco a ogni ora invece di una
        transizione graduale."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        valore_8_30 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 8, 30), 0.0, 50.0)
        valore_8_59 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 8, 59), 0.0, 50.0)
        valore_9_00 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 9, 0), 0.0, 50.0)
        valore_9_01 = genera_bagnatura_fogliare(datetime(2026, 7, 1, 9, 1), 0.0, 50.0)
        # con l'ora frazionaria, 8:59 e 9:01 devono restare vicini fra loro;
        # con l'ora intera, 8:59 resterebbe congelato al valore delle 8:00
        # (notturno, ~55) mentre 9:00 salterebbe di colpo al ramo diurno (~17).
        assert valore_8_59 == pytest.approx(valore_9_00, abs=1.0), (
            "Salto brusco fra le 8:59 e le 9:00: la transizione non è continua entro l'ora."
        )
        assert valore_9_00 == pytest.approx(valore_9_01, abs=1.0)
        assert valore_8_30 > valore_8_59, "Il valore alle 8:30 dovrebbe essere ancora più alto di quello alle 8:59 (si sta asciugando)."


class TestGeneraVelocitaVento:
    """Anemometro sul nodo meteo: alimenta il raffreddamento di temperatura_bacca
    (v. TestCorrelazioneVentoTempBacca), quindi il valore pubblicato deve
    essere fisicamente plausibile indipendentemente da quel consumo."""

    def test_mai_negativa(self, monkeypatch):
        monkeypatch.setattr(random, "uniform", lambda a, b: a)  # rumore al minimo dichiarato
        for ora in range(24):
            valore = genera_velocita_vento(datetime(2026, 7, 1, ora, 0), "normale")
            assert valore >= 0.0

    def test_piu_tesa_a_meta_giornata_che_di_notte(self, monkeypatch):
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)  # rumore azzerato
        notte = genera_velocita_vento(datetime(2026, 7, 1, 3, 0), "normale")
        meta_giornata = genera_velocita_vento(datetime(2026, 7, 1, 13, 0), "normale")
        assert meta_giornata > notte

    def test_ridotta_in_ondata_di_calore_a_parita_di_ora(self, monkeypatch):
        """Le ondate di calore sono tipicamente associate ad aria stagnante:
        a parità di ora, il vento atteso deve essere più basso, non più alto."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        normale = genera_velocita_vento(datetime(2026, 7, 1, 13, 0), "normale")
        ondata = genera_velocita_vento(datetime(2026, 7, 1, 13, 0), "ondata_di_calore")
        assert ondata < normale


class TestGeneraTemperaturaSuolo:
    """Stesso principio fisico della temperatura dell'aria, ma con inerzia
    termica maggiore: ampiezza giornaliera più contenuta, picco ritardato,
    risposta smorzata a un'ondata di calore."""

    def test_ampiezza_giornaliera_minore_dell_aria(self, monkeypatch):
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        valori_suolo = [genera_temperatura_suolo(datetime(2026, 7, 1, h, 0), "normale") for h in range(24)]
        valori_aria = [genera_temp_aria(datetime(2026, 7, 1, h, 0), "normale") for h in range(24)]
        ampiezza_suolo = max(valori_suolo) - min(valori_suolo)
        ampiezza_aria = max(valori_aria) - min(valori_aria)
        assert ampiezza_suolo < ampiezza_aria, (
            "Il suolo, per inerzia termica, deve oscillare meno dell'aria nello stesso giorno."
        )

    def test_picco_ritardato_rispetto_all_aria(self, monkeypatch):
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        ore = [h + m / 60 for h in range(24) for m in (0, 15, 30, 45)]
        ora_picco_suolo = max(ore, key=lambda o: genera_temperatura_suolo(datetime(2026, 7, 1, int(o), int((o % 1) * 60)), "normale"))
        ora_picco_aria = max(ore, key=lambda o: genera_temp_aria(datetime(2026, 7, 1, int(o), int((o % 1) * 60)), "normale"))
        assert ora_picco_suolo > ora_picco_aria, "Il picco di temperatura del suolo deve arrivare dopo quello dell'aria (15:00)."

    def test_soglia_danno_radicale_raggiungibile_in_ondata_di_calore(self, monkeypatch):
        tetto = _temp_suolo_max_teorico("ondata_di_calore", monkeypatch)
        assert tetto > SOGLIA_DANNO_RADICALE_C, (
            f"Tetto teorico temperatura_suolo in ondata_di_calore ({tetto:.2f}°C) "
            f"sotto la soglia di danno_radicale (35°C): l'allerta non scatterebbe mai."
        )

    @pytest.mark.parametrize("scenario", ["normale", "stress_idrico"])
    def test_soglia_danno_radicale_non_raggiungibile_fuori_scenario(self, scenario, monkeypatch):
        tetto = _temp_suolo_max_teorico(scenario, monkeypatch)
        assert tetto < SOGLIA_DANNO_RADICALE_C, (
            f"Tetto teorico temperatura_suolo in '{scenario}' ({tetto:.2f}°C) "
            f"vicino o oltre i 35°C: danno_radicale scatterebbe anche fuori "
            f"da un'ondata di calore, fisicamente incoerente."
        )

    def test_smorzato_fuori_dal_picco_in_ondata_di_calore(self, monkeypatch):
        """Fuori dalla finestra del picco (14-17), il rialzo del suolo deve
        restare trascurabile rispetto ai +13°C costanti dell'aria."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        for ora in (8, 10, 20, 22):
            dt = datetime(2026, 7, 1, ora, 0)
            delta_suolo = genera_temperatura_suolo(dt, "ondata_di_calore") - genera_temperatura_suolo(dt, "normale")
            delta_aria = genera_temp_aria(dt, "ondata_di_calore") - genera_temp_aria(dt, "normale")
            assert delta_suolo < delta_aria, f"Alle {ora}:00 il rialzo del suolo non è smorzato rispetto all'aria."

    def test_puo_superare_il_boost_costante_dell_aria_al_centro_del_picco(self, monkeypatch):
        """Nelle ore centrali del picco stretto, l'aggiunta istantanea del
        suolo supera il boost costante dell'aria — coerente con l'esposizione
        diretta al sole di una superficie di terreno nudo, non un difetto:
        se questo test tornasse a fallire, vorrebbe dire che il picco non è
        più abbastanza alto da raggiungere danno_radicale con margine."""
        monkeypatch.setattr(random, "uniform", lambda a, b: 0.0)
        dt = datetime(2026, 7, 1, 15, 0)
        delta_suolo = genera_temperatura_suolo(dt, "ondata_di_calore") - genera_temperatura_suolo(dt, "normale")
        delta_aria = genera_temp_aria(dt, "ondata_di_calore") - genera_temp_aria(dt, "normale")
        assert delta_suolo > delta_aria