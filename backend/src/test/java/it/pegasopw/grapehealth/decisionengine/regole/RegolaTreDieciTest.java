package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.cache.CacheTabellaGoidanich;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichId;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.repository.ParcellaRepository;
import it.pegasopw.grapehealth.decisionengine.repository.SogliaIncubazioneGoidanichRepository;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.BeforeEach;
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

    private RegolaTreDieci regola;
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-04-15T10:00:00Z");

    private ParcellaEntity parcella(String nome, double lunghezzaCm) {
        ParcellaEntity entity = new ParcellaEntity();
        entity.setNome(nome);
        entity.setLunghezzaGermoglioCm(lunghezzaCm);
        return entity;
    }

    @BeforeEach
    void creaRegolaConCacheDiTest() {
        ParcellaRepository parcellaRepository = mock(ParcellaRepository.class);
        when(parcellaRepository.findAll()).thenReturn(List.of(
                parcella("parcellaA", 12.0),
                parcella("parcellaB", 14.0),
                parcella("parcellaC", 10.0)));
        CacheGermogli cacheGermogli = new CacheGermogli(parcellaRepository);
        cacheGermogli.carica();

        SogliaIncubazioneGoidanichRepository goidanichRepository = mock(SogliaIncubazioneGoidanichRepository.class);
        when(goidanichRepository.findAll()).thenReturn(List.of(
                rigaGoidanich(14, false, 6.6), rigaGoidanich(14, true, 9.0),
                rigaGoidanich(20, false, 14.2), rigaGoidanich(20, true, 20.0)));
        CacheTabellaGoidanich cacheTabellaGoidanich = new CacheTabellaGoidanich(goidanichRepository);
        cacheTabellaGoidanich.carica();

        regola = new RegolaTreDieci(cacheGermogli, cacheTabellaGoidanich);
    }

    private SogliaIncubazioneGoidanichEntity rigaGoidanich(int temperatura, boolean umiditaAlta, double percentuale) {
        SogliaIncubazioneGoidanichEntity entity = new SogliaIncubazioneGoidanichEntity();
        entity.setId(new SogliaIncubazioneGoidanichId(temperatura, umiditaAlta));
        entity.setPercentualeIncrementoGiornaliero(percentuale);
        return entity;
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
        // Due giorni diversi, ciascuno sotto soglia singolarmente (4mm, 7mm), che
        // insieme superano i 10mm richiesti dalla ricerca di Baldacci: è questo 
        // il caso che la finestra di 48h è pensata per catturare.
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 4.0, ora), stato);

        Instant giornoDopo = ora.plus(24, ChronoUnit.HOURS);
        regola.valuta(temperatura("parcellaA", 14.0, giornoDopo), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 7.0, giornoDopo), stato);

        assertTrue(risultato.isPresent());
    }

    @Test
    void nonGonfiaLaPioggiaSuLetturePeripeteNelloStessoGiornoSimulato() {
        // Riproduce il comportamento reale del simulatore (generator.py): "pioggia" è
        // un totale di giornata ripubblicato invariato a ogni ciclo (~2880 volte/giorno
        // simulato), non una lettura incrementale indipendente.
        Instant t = ora;
        for (int i = 0; i < 5; i++) {
            regola.valuta(pioggia("parcellaA", 4.0, t), stato);
            t = t.plus(30, ChronoUnit.SECONDS); // stesso intervallo di pubblicazione del simulatore
        }

        double cumulato = stato.sommaFinestra("tre_dieci:parcellaA:pioggia", t, Duration.ofHours(48));
        assertEquals(4.0, cumulato, 0.001); // non 20.0 (4.0 × 5 letture duplicate)
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

        // A 20°C con umidità alta, la ricerca di Goidanich indica il 20% di sviluppo al
        // giorno: servono 4 giorni per superare la soglia del 70%.
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

        // Dopo 3 giorni la pioggia iniziale è ormai fuori dalla finestra
        // delle 48h (condizioneVerificata tornerebbe falsa), ma l'incubazione
        // già avviata deve proseguire lo stesso.
        Instant treGiorniDopo = ora.plus(3, ChronoUnit.DAYS);
        regola.valuta(temperatura("parcellaA", 20.0, treGiorniDopo), stato);
        regola.valuta(umidita("parcellaA", 95.0, treGiorniDopo), stato);

        assertEquals("moderato", stato.livelloRischio("tre_dieci:parcellaA"));
        assertTrue(stato.percentualeIncubazione("tre_dieci:parcellaA") > 0);
    }
}