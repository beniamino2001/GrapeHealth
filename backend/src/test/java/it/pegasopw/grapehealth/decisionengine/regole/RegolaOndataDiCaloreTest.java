package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RegolaOndataDiCaloreTest {

    private final RegolaOndataDiCalore regola = new RegolaOndataDiCalore();
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

        // Temperatura dichiarata 34.5, la quale è sotto soglia grezza (35) ma dentro il margine di isteresi (35-1.0=34)
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
}