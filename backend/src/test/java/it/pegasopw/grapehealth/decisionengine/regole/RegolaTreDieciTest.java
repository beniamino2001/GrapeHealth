package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.cache.CacheTabellaGoidanich;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichId;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.repository.ParcellaRepository;
import it.pegasopw.grapehealth.decisionengine.repository.SogliaIncubazioneGoidanichRepository;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegolaTreDieciTest {

    private final CacheGermogli cacheGermogli = cacheGermogli();
    private final CacheTabellaGoidanich cacheTabellaGoidanich = cacheTabellaGoidanich();
    private final RegolaTreDieci regola = new RegolaTreDieci(cacheGermogli, cacheTabellaGoidanich, cacheSoglieRegole());
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-04-15T10:00:00Z");

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private RegolaSogliaEntity soglia(double valore, int durataMinuti) {
        RegolaSogliaEntity s = soglia(valore);
        s.setDurataMinimaMinuti(durataMinuti);
        return s;
    }

    private CacheSoglieRegole cacheSoglieRegole() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("tre_dieci", "temperatura_aria", "moderato")).thenReturn(soglia(10.0));
        when(cache.sogliaUnica("tre_dieci", "pioggia", "moderato")).thenReturn(soglia(10.0, 2880));
        when(cache.sogliaUnica("tre_dieci", "germogli", "moderato")).thenReturn(soglia(10.0));
        return cache;
    }

    private ParcellaEntity parcella(String nome, double lunghezzaCm) {
        ParcellaEntity entity = new ParcellaEntity();
        entity.setNome(nome);
        entity.setLunghezzaGermoglioCm(lunghezzaCm);
        return entity;
    }

    private CacheGermogli cacheGermogli() {
        ParcellaRepository parcellaRepository = mock(ParcellaRepository.class);
        when(parcellaRepository.findAll()).thenReturn(List.of(
                parcella("parcellaA", 12.0),
                parcella("parcellaB", 14.0),
                parcella("parcellaC", 10.0)));
        CacheGermogli cache = new CacheGermogli(parcellaRepository);
        cache.carica();
        return cache;
    }

    private SogliaIncubazioneGoidanichEntity rigaGoidanich(int temperatura, boolean umiditaAlta, double percentuale) {
        SogliaIncubazioneGoidanichEntity entity = new SogliaIncubazioneGoidanichEntity();
        entity.setId(new SogliaIncubazioneGoidanichId(temperatura, umiditaAlta));
        entity.setPercentualeIncrementoGiornaliero(percentuale);
        return entity;
    }

    private CacheTabellaGoidanich cacheTabellaGoidanich() {
        SogliaIncubazioneGoidanichRepository goidanichRepository = mock(SogliaIncubazioneGoidanichRepository.class);
        when(goidanichRepository.findAll()).thenReturn(List.of(
                rigaGoidanich(14, false, 6.6), rigaGoidanich(14, true, 9.0),
                rigaGoidanich(20, false, 14.2), rigaGoidanich(20, true, 20.0)));
        CacheTabellaGoidanich cache = new CacheTabellaGoidanich(goidanichRepository);
        cache.carica();
        return cache;
    }

    private MisurazioneMessage temperatura(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "temperatura_aria", valore, "C", t);
    }

    private MisurazioneMessage pioggia(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "pioggia", valore, "mm", t);
    }

    private MisurazioneMessage umidita(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "umidita_aria", valore, "%", t);
    }

    @Test
    void scattaQuandoTutteETreLeCondizioniSonoVerificate() {
        regola.valuta(temperatura("parcellaC", 12.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaC", 11.0, ora), stato);

        assertTrue(risultato.isPresent());
        assertEquals("tre_dieci", risultato.get().tipo());
    }

    @Test
    void nonScattaSeLaTemperaturaENonAncoraNota() {
        var risultato = regola.valuta(pioggia("parcellaA", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLaPioggiaCumulataENonAncoraSufficiente() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 4.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void sommaCorrettamentePioggeSuGiorniDiversiNellaFinestra() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 4.0, ora), stato);

        Instant giornoDopo = ora.plus(24, ChronoUnit.HOURS);
        regola.valuta(temperatura("parcellaA", 14.0, giornoDopo), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 7.0, giornoDopo), stato);

        assertTrue(risultato.isPresent());
    }

    @Test
    void nonGonfiaLaPioggiaSuLetturePeripeteNelloStessoGiornoSimulato() {
        Instant t = ora;
        for (int i = 0; i < 5; i++) {
            regola.valuta(pioggia("parcellaA", 4.0, t), stato);
            t = t.plus(30, ChronoUnit.SECONDS);
        }

        double cumulato = stato.sommaFinestra("tre_dieci:parcellaA:pioggia", t, Duration.ofHours(48));
        assertEquals(4.0, cumulato, 0.001);
    }

    @Test
    void escludePioggiaFuoriDallaFinestraDi48Ore() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 8.0, ora), stato);

        var risultato = regola.valuta(
                pioggia("parcellaA", 3.0, ora.plus(50, ChronoUnit.HOURS)), stato);

        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLoStadioFenologicoENonSufficiente() {
        regola.valuta(temperatura("parcellaSconosciuta", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaSconosciuta", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonRipubblicaSeLaCondizioneRestaVerificata() {
        regola.valuta(temperatura("parcellaB", 15.0, ora), stato);
        regola.valuta(pioggia("parcellaB", 11.0, ora), stato);

        var secondoGiro = regola.valuta(pioggia("parcellaB", 1.0, ora.plus(1, ChronoUnit.HOURS)), stato);
        assertTrue(secondoGiro.isEmpty());
    }

    @Test
    void ignoraParametriNonPertinenti() {
        var m = new MisurazioneMessage("idrico-A1", "parcellaA", "psi_stem", -1.5, "MPa", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void accumulaIncubazioneEScalaASeveroAlRaggiungimentoDellaSogliaDiTrattamento() {
        regola.valuta(temperatura("parcellaA", 20.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 11.0, ora), stato);
        regola.valuta(umidita("parcellaA", 95.0, ora), stato);

        Optional<AllertaEvent> risultato = Optional.empty();
        for (int giorno = 1; giorno <= 4 && risultato.isEmpty(); giorno++) {
            Instant t = ora.plus(giorno, ChronoUnit.DAYS);
            risultato = regola.valuta(temperatura("parcellaA", 20.0, t), stato);
            if (risultato.isEmpty()) {
                risultato = regola.valuta(umidita("parcellaA", 95.0, t), stato);
            }
        }

        assertTrue(risultato.isPresent());
        assertEquals("severo", risultato.get().livelloRischio());
    }

    @Test
    void nonScalaASeveroSeLaSogliaDiTrattamentoNonEAncoraRaggiunta() {
        regola.valuta(temperatura("parcellaA", 20.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 11.0, ora), stato);
        regola.valuta(umidita("parcellaA", 95.0, ora), stato);

        Instant unGiornoDopo = ora.plus(1, ChronoUnit.DAYS);
        var r1 = regola.valuta(temperatura("parcellaA", 20.0, unGiornoDopo), stato);
        var r2 = regola.valuta(umidita("parcellaA", 95.0, unGiornoDopo), stato);

        assertTrue(r1.isEmpty());
        assertTrue(r2.isEmpty());
        assertEquals("moderato", stato.livelloRischio("tre_dieci:parcellaA"));
        assertEquals(20.0, stato.percentualeIncubazione("tre_dieci:parcellaA"), 0.001);
    }

    @Test
    void lIncubazioneProseguelAncheSeLaPioggiaEsceDallaFinestraDelle48Ore() {
        regola.valuta(temperatura("parcellaA", 20.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 11.0, ora), stato);
        regola.valuta(umidita("parcellaA", 95.0, ora), stato);

        Instant treGiorniDopo = ora.plus(3, ChronoUnit.DAYS);
        regola.valuta(temperatura("parcellaA", 20.0, treGiorniDopo), stato);
        regola.valuta(umidita("parcellaA", 95.0, treGiorniDopo), stato);

        assertEquals("moderato", stato.livelloRischio("tre_dieci:parcellaA"));
        assertTrue(stato.percentualeIncubazione("tre_dieci:parcellaA") > 0);
    }
}