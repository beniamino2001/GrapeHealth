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

class RegolaOndataDiCaloreTest {

    private final RegolaOndataDiCalore regola = new RegolaOndataDiCalore(cacheSoglieRegole());

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private CacheSoglieRegole cacheSoglieRegole() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("ondata_di_calore", "temperatura_aria", "moderato")).thenReturn(soglia(35.0));
        when(cache.sogliaUnica("ondata_di_calore", "temperatura_aria", "severo")).thenReturn(soglia(40.0));
        return cache;
    }
    private final StatoRischio stato = new StatoRischio();

    private MisurazioneMessage misurazioneTemperaturaAria(double valore) {
        return new MisurazioneMessage("meteo-A1", "parcellaA", "temperatura_aria", valore, "C", Instant.now());
    }

    @Test
    void nonScattaSottoSoglia() {
        assertTrue(regola.valuta(misurazioneTemperaturaAria(34.9), stato).isEmpty());
    }

    @Test
    void scattaAppenaSopraSoglia() {
        var risultato = regola.valuta(misurazioneTemperaturaAria(35.1), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void nonRipubblicaSeCondizioneInvariata() {
        var m = misurazioneTemperaturaAria(36.0);
        regola.valuta(m, stato);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void nonRientraPerPiccoleOscillazioniEntroIsteresi() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato); // scatta

        // sotto soglia grezza (35) ma dentro il margine di isteresi (35-1.0=34)
        var oscillazione = regola.valuta(misurazioneTemperaturaAria(34.5), stato);
        assertTrue(oscillazione.isEmpty());
    }

    @Test
    void rientraDavveroOltreIlMargineDiIsteresi() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato); // scatta
        regola.valuta(misurazioneTemperaturaAria(33.5), stato); // rientro genuino (< 34)

        var nuovoSuperamento = regola.valuta(misurazioneTemperaturaAria(35.5), stato);
        assertTrue(nuovoSuperamento.isPresent());
    }

    @Test
    void ignoraParametriDiversiDaTemperaturaAria() {
        var m = new MisurazioneMessage("bacca-A1", "parcellaA", "temperatura_bacca", 40.0, "C", Instant.now());
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void scattaSeveroAppenaSopraDi40() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato); // prima "moderato"
        var risultato = regola.valuta(misurazioneTemperaturaAria(40.1), stato);
        assertTrue(risultato.isPresent());
        assertEquals("severo", risultato.get().livelloRischio());
    }

    @Test
    void nonRipubblicaSeLivelloSeveroInvariato() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato);
        regola.valuta(misurazioneTemperaturaAria(40.1), stato); // severo
        assertTrue(regola.valuta(misurazioneTemperaturaAria(40.5), stato).isEmpty());
    }

    @Test
    void scalaGradualmenteDaSeveroAModeratoEntroIsteresi() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato);
        regola.valuta(misurazioneTemperaturaAria(40.1), stato); // severo

        // 39.5: sotto soglia severa grezza (40) ma dentro isteresi severo (40-1=39)
        var risultato = regola.valuta(misurazioneTemperaturaAria(39.5), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void rientraDaSeveroAModeratoGenuinamente() {
        regola.valuta(misurazioneTemperaturaAria(36.0), stato);
        regola.valuta(misurazioneTemperaturaAria(40.1), stato); // severo

        // 38.5: sotto isteresi severo (39) ma ancora sopra moderato — torna a moderato
        var rientroModerato = regola.valuta(misurazioneTemperaturaAria(38.5), stato);
        assertTrue(rientroModerato.isPresent());
        assertEquals("moderato", rientroModerato.get().livelloRischio());
    }
}