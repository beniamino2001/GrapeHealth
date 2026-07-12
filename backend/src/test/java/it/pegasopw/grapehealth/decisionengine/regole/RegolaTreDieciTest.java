package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RegolaTreDieciTest {

    private final RegolaTreDieci regola = new RegolaTreDieci();
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-04-15T10:00:00Z"); // data in cui c'è un germogliamento primaverile plausibile

    private MisurazioneMessage temperatura(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "temperatura_aria", valore, "C", t);
    }

    private MisurazioneMessage pioggia(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "pioggia", valore, "mm", t);
    }

    @Test
    void scattaQuandoTutteETreLeCondizioniSonoVerificate() {
        // parcellaC ha germogli a 10cm esattamente (soglia inclusiva, "≥10")
        regola.valuta(temperatura("parcellaC", 12.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaC", 11.0, ora), stato);

        assertTrue(risultato.isPresent());
        assertEquals("tre_dieci", risultato.get().tipo());
    }

    @Test
    void nonScattaSeLaTemperaturaENonAncoraNota() {
        // arriva solo la pioggia, nessuna temperatura mai registrata per questa parcella
        var risultato = regola.valuta(pioggia("parcellaA", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLaPioggiaCumulataENonAncoraSufficiente() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 4.0, ora), stato); // sotto i 10mm
        assertTrue(risultato.isEmpty());
    }

    @Test
    void sommaCorrettamentePioggeMultipleNellaFinestra() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 4.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 3.0, ora.plus(6, ChronoUnit.HOURS)), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 4.0, ora.plus(12, ChronoUnit.HOURS)), stato); // totale 11mm

        assertTrue(risultato.isPresent());
    }

    @Test
    void escludePioggiaFuoriDallaFinestraDi48Ore() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 8.0, ora), stato); // fuori finestra rispetto all'ultima lettura sotto

        var risultato = regola.valuta(
                pioggia("parcellaA", 3.0, ora.plus(50, ChronoUnit.HOURS)), stato); // oltre 48h dalla prima lettura

        // 8mm ormai fuori dalla finestra + 3mm nuovi = 3mm cumulati, sotto soglia
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLoStadioFenologicoENonSufficiente() {
        // germogli inventati sotto soglia per una parcella ipotetica non mappata
        // (getOrDefault restituisce 0.0 -> condizione mai verificata)
        regola.valuta(temperatura("parcellaSconosciuta", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaSconosciuta", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonRipubblicaSeLaCondizioneRestaVerificata() {
        regola.valuta(temperatura("parcellaB", 15.0, ora), stato);
        regola.valuta(pioggia("parcellaB", 11.0, ora), stato); // scatta

        var secondoGiro = regola.valuta(pioggia("parcellaB", 1.0, ora.plus(1, ChronoUnit.HOURS)), stato);
        assertTrue(secondoGiro.isEmpty()); // condizione ancora vera, non ripubblica
    }

    @Test
    void ignoraParametriNonPertinenti() {
        var m = new MisurazioneMessage("idrico-A1", "parcellaA", "psi_stem", -1.5, "MPa", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }
}