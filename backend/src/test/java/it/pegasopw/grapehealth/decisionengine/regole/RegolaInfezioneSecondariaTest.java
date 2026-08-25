package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegolaInfezioneSecondariaTest {

    private final RegolaInfezioneSecondaria regola = new RegolaInfezioneSecondaria(cacheSoglieRegole());

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
        when(cache.sogliaUnica("infezione_secondaria", "bagnatura_fogliare", "moderato")).thenReturn(soglia(40.0, 120));
        when(cache.sogliaUnica("infezione_secondaria", "temperatura_aria", "moderato", ">=")).thenReturn(soglia(4.0));
        when(cache.sogliaUnica("infezione_secondaria", "temperatura_aria", "moderato", "<=")).thenReturn(soglia(30.2));
        return cache;
    }
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-04-15T20:00:00Z");

    private MisurazioneMessage bagnatura(double valore, Instant t) {
        return new MisurazioneMessage("meteo-A1", "parcellaA", "bagnatura_fogliare", valore, "%", t);
    }

    private MisurazioneMessage temperatura(double valore, Instant t) {
        return new MisurazioneMessage("meteo-A1", "parcellaA", "temperatura_aria", valore, "C", t);
    }

    @Test
    void nonScattaSeLaBagnaturaNonRaggiungeMaiLaSoglia() {
        regola.valuta(temperatura(20.0, ora), stato);
        assertTrue(regola.valuta(bagnatura(30.0, ora), stato).isEmpty());
    }

    @Test
    void nonScattaSeLaDurataENonAncoraSufficiente() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        // solo 60 minuti dopo l'inizio dell'episodio, non ancora i 120 richiesti
        var risultato = regola.valuta(bagnatura(45.0, ora.plus(60, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void scattaConDurataETemperaturaSufficienti() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        var risultato = regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void nonScattaSeLaTemperaturaEFuoriBanda() {
        regola.valuta(temperatura(2.0, ora), stato); // sotto i 4.0°C richiesti
        regola.valuta(bagnatura(45.0, ora), stato);
        var risultato = regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonInterrompeLEpisodioPerUnaLetturaRumorosaEntroListeresi() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        // scende a 38%, dentro il margine di isteresi (soglia 40 - 5 = 35): l'episodio
        // deve continuare
        regola.valuta(bagnatura(38.0, ora.plus(60, ChronoUnit.MINUTES)), stato);
        var risultato = regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isPresent());
    }

    @Test
    void interrompeLEpisodioSeLaBagnaturaScendeSottoListeresi() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        // scende a 30%, sotto il margine di isteresi: l'episodio si interrompe davvero
        regola.valuta(bagnatura(30.0, ora.plus(60, ChronoUnit.MINUTES)), stato);
        // l'episodio è ripartito da zero a +60min: 125 minuti dall'inizio originale non
        // bastano più
        var risultato = regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonRipubblicaSeLivelloRischioInvariato() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato); // scatta

        var secondaLettura = regola.valuta(bagnatura(45.0, ora.plus(130, ChronoUnit.MINUTES)), stato);
        assertTrue(secondaLettura.isEmpty());
    }

    @Test
    void ignoraParametriNonPertinenti() {
        var m = new MisurazioneMessage("meteo-A1", "parcellaA", "pioggia", 5.0, "mm", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void nonInterrompeLaTemperaturaFavorevolePerUnaLetturaAppenaSopraLaBandaEntroIsteresi() {
        regola.valuta(temperatura(20.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora), stato);
        regola.valuta(bagnatura(45.0, ora.plus(125, ChronoUnit.MINUTES)), stato); // scatta

        // 30.8°C: sopra il confine stretto (30.2) ma dentro il margine di isteresi
        // (30.2+1=31.2)
        var risultato = regola.valuta(temperatura(30.8, ora.plus(130, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isEmpty());
    }
}