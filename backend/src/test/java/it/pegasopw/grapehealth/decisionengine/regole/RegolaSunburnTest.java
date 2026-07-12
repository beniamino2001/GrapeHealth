package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RegolaSunburnTest {

    private final RegolaSunburn regola = new RegolaSunburn();
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-07-15T13:00:00Z");

    private MisurazioneMessage temperaturaBacca(double valore, Instant t) {
        return new MisurazioneMessage("bacca-A1", "parcellaA", "temperatura_bacca", valore, "C", t);
    }

    @Test
    void nonScattaSottoSoglia() {
        assertTrue(regola.valuta(temperaturaBacca(44.0, ora), stato).isEmpty());
    }

    @Test
    void scattaModeratoAppenaSopraSogliaSenzaDurataSufficiente() {
        var risultato = regola.valuta(temperaturaBacca(46.0, ora), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void restaModeratoSeTemperaturaAltaMaDurataInsufficientePerLaSuaSogliaLT50() {
        // 47.82°C richiede 60 minuti continuativi per diventare severo, secondo la ricerca di Schmidt nel 2023
        regola.valuta(temperaturaBacca(47.82, ora), stato); // apre l'episodio
        var dopo30Min = regola.valuta(temperaturaBacca(47.82, ora.plus(30, ChronoUnit.MINUTES)), stato);
        assertTrue(dopo30Min.isEmpty()); // stesso livello "moderato", nessuna ripubblicazione
    }

    @Test
    void scattaSeveroAlRaggiungimentoDellaDurataLT50() {
        regola.valuta(temperaturaBacca(47.82, ora), stato); // apre l'episodio a 47.82°C
        var dopo60Min = regola.valuta(temperaturaBacca(47.82, ora.plus(60, ChronoUnit.MINUTES)), stato);

        assertTrue(dopo60Min.isPresent());
        assertEquals("severo", dopo60Min.get().livelloRischio());
    }

    @Test
    void scattaSeveroSubitoSeTemperaturaMoltoAltaAncheConDurataBreve() {
        regola.valuta(temperaturaBacca(54.0, ora), stato); // sopra 53.79°C, soglia dei 15 min
        var dopo15Min = regola.valuta(temperaturaBacca(54.0, ora.plus(15, ChronoUnit.MINUTES)), stato);

        assertTrue(dopo15Min.isPresent());
        assertEquals("severo", dopo15Min.get().livelloRischio());
    }

    @Test
    void nonScattaSeveroSeLaTemperaturaNonRaggiungeAlcunaSogliaLetale() {
        // 46°C non è mai una soglia di Schmidt, indipendentemente dalla durata
        regola.valuta(temperaturaBacca(46.0, ora), stato);
        var dopo2Ore = regola.valuta(temperaturaBacca(46.0, ora.plus(120, ChronoUnit.MINUTES)), stato);
        assertTrue(dopo2Ore.isEmpty()); // resta "moderato", nessuna nuova pubblicazione
    }

    @Test
    void rientraDavveroSottoIlMargineDiIsteresiEResettaLaDurata() {
        regola.valuta(temperaturaBacca(47.82, ora), stato); // apre episodio

        regola.valuta(temperaturaBacca(43.0, ora.plus(10, ChronoUnit.MINUTES)), stato); // rientro genuino (< 44)

        // nuovo episodio: anche se sono passati 60 min dal primo, l'esposizione è ricominciata da capo
        var risultato = regola.valuta(temperaturaBacca(47.82, ora.plus(70, ChronoUnit.MINUTES)), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio()); // non ancora 60 min dal nuovo inizio
    }

    @Test
    void nonRientraPerOscillazioniEntroIsteresi() {
        regola.valuta(temperaturaBacca(47.82, ora), stato);
        // 44.5: sotto i 45 ma dentro il margine di isteresi (45-1.0=44)
        var oscillazione = regola.valuta(temperaturaBacca(44.5, ora.plus(20, ChronoUnit.MINUTES)), stato);
        assertTrue(oscillazione.isEmpty());

        // la durata deve essere sopravvissuta all'oscillazione: a 60 min totali scatta severo
        var dopo60MinTotali = regola.valuta(temperaturaBacca(47.82, ora.plus(60, ChronoUnit.MINUTES)), stato);
        assertTrue(dopo60MinTotali.isPresent());
        assertEquals("severo", dopo60MinTotali.get().livelloRischio());
    }

    @Test
    void ignoraParametriDiversiDaTemperaturaBacca() {
        var m = new MisurazioneMessage("meteo-A1", "parcellaA", "temperatura_aria", 40.0, "C", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void unaVoltaRaggiuntoSeveroNonRetrocedeAModeratoNelloStessoEpisodio() {
        regola.valuta(temperaturaBacca(54.0, ora), stato); // apre episodio, sopra soglia 15 min
        regola.valuta(temperaturaBacca(54.0, ora.plus(15, ChronoUnit.MINUTES)), stato); // severo raggiunto

        // lettura successiva più bassa ma ancora nel range di rischio (sopra 45°C): non deve retrocedere il livello a moderato all'interno dello stesso episodio
        var dopoOscillazione = regola.valuta(temperaturaBacca(46.0, ora.plus(20, ChronoUnit.MINUTES)), stato);
        assertTrue(dopoOscillazione.isEmpty()); // nessuna ripubblicazione: il livello resta "severo"
    }
}