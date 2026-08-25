package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegolaSvernamentoOosporeTest {

    private final RegolaSvernamentoOospore regola = new RegolaSvernamentoOospore(cacheSoglieRegole());

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private CacheSoglieRegole cacheSoglieRegole() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("svernamento_oospore", "temperatura_suolo", "moderato", ">=")).thenReturn(soglia(12.0));
        when(cache.sogliaUnica("svernamento_oospore", "temperatura_suolo", "moderato", "<=")).thenReturn(soglia(32.0));
        return cache;
    }
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-01-15T10:00:00Z");

    private MisurazioneMessage misurazione(double valore) {
        return new MisurazioneMessage("suolo-A1", "parcellaA", "temperatura_suolo", valore, "C", ora);
    }

    @Test
    void nonScattaSottoLaSogliaMinima() {
        assertTrue(regola.valuta(misurazione(11.9), stato).isEmpty());
    }

    @Test
    void nonScattaSopraLaSogliaMassima() {
        assertTrue(regola.valuta(misurazione(32.1), stato).isEmpty());
    }

    @Test
    void scattaAllaSogliaMinimaInclusa() {
        var risultato = regola.valuta(misurazione(12.0), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void scattaAllaSogliaMassimaInclusa() {
        var risultato = regola.valuta(misurazione(32.0), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void scattaAllInternoDellaBanda() {
        var risultato = regola.valuta(misurazione(20.0), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void nonRipubblicaSeLivelloRischioInvariato() {
        regola.valuta(misurazione(20.0), stato);
        assertTrue(regola.valuta(misurazione(20.0), stato).isEmpty());
    }

    @Test
    void ripubblicaDopoUnaGenuinaUscitaERientroDallaBanda() {
        regola.valuta(misurazione(20.0), stato); // scatta
        regola.valuta(misurazione(35.0), stato);  // esce dalla banda: azzera lo stato

        var nuovoIngresso = regola.valuta(misurazione(20.0), stato); // rientra
        assertTrue(nuovoIngresso.isPresent());
    }

    @Test
    void ignoraParametriDiversiDaTemperaturaSuolo() {
        var m = new MisurazioneMessage("suolo-A1", "parcellaA", "umidita_suolo", 20.0, "%", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void nonRientraPerUnaLetturaAppenaSopraLaSogliaMassimaEntroIsteresi() {
        regola.valuta(misurazione(20.0), stato); // scatta
        // 32.5: sopra il confine stretto (32) ma dentro il margine di isteresi (32+1=33)
        var risultato = regola.valuta(misurazione(32.5), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonRientraPerUnaLetturaAppenaSottoLaSogliaMinimaEntroIsteresi() {
        regola.valuta(misurazione(20.0), stato); // scatta
        // 11.5: sotto il confine stretto (12) ma dentro il margine di isteresi (12-1=11)
        var risultato = regola.valuta(misurazione(11.5), stato);
        assertTrue(risultato.isEmpty());
    }
}