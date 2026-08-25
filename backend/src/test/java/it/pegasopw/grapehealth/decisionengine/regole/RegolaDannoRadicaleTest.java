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

class RegolaDannoRadicaleTest {

    private final RegolaDannoRadicale regola = new RegolaDannoRadicale(cacheSoglieRegole());

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private CacheSoglieRegole cacheSoglieRegole() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("danno_radicale", "temperatura_suolo", "severo")).thenReturn(soglia(35.0));
        return cache;
    }
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-07-15T14:00:00Z");

    private MisurazioneMessage misurazione(double valore) {
        return new MisurazioneMessage("suolo-A1", "parcellaA", "temperatura_suolo", valore, "C", ora);
    }

    @Test
    void nonScattaSottoSoglia() {
        assertTrue(regola.valuta(misurazione(34.9), stato).isEmpty());
    }

    @Test
    void scattaAllaSogliaEsatta() {
        var risultato = regola.valuta(misurazione(35.0), stato);
        assertTrue(risultato.isPresent());
        assertEquals("severo", risultato.get().livelloRischio());
    }

    @Test
    void scattaSopraSoglia() {
        var risultato = regola.valuta(misurazione(36.0), stato);
        assertTrue(risultato.isPresent());
        assertEquals("severo", risultato.get().livelloRischio());
    }

    @Test
    void nonRipubblicaSeLivelloRischioInvariato() {
        regola.valuta(misurazione(35.5), stato);
        assertTrue(regola.valuta(misurazione(35.5), stato).isEmpty());
    }

    @Test
    void nonRientraPerOscillazioniPiccoleEntroListeresi() {
        regola.valuta(misurazione(35.0), stato); // scatta
        // scende a 34.5, dentro il margine di isteresi (soglia 35 - 1 = 34): resta attivo
        var risultato = regola.valuta(misurazione(34.5), stato);
        assertTrue(risultato.isEmpty());

        // un nuovo superamento non deve ripubblicare: la condizione non si è mai davvero interrotta
        var secondoSuperamento = regola.valuta(misurazione(35.5), stato);
        assertTrue(secondoSuperamento.isEmpty());
    }

    @Test
    void rientraGenuinamenteSottoListeresi() {
        regola.valuta(misurazione(35.0), stato); // scatta
        var rientro = regola.valuta(misurazione(33.9), stato); // sotto 34: rientro genuino
        assertTrue(rientro.isEmpty());
    }

    @Test
    void ripubblicaDopoUnaGenuinaUscitaERientro() {
        regola.valuta(misurazione(35.0), stato); // scatta
        regola.valuta(misurazione(33.9), stato);  // rientro genuino: azzera lo stato

        var nuovoSuperamento = regola.valuta(misurazione(35.0), stato); // ricompare
        assertTrue(nuovoSuperamento.isPresent());
    }

    @Test
    void ignoraParametriDiversiDaTemperaturaSuolo() {
        var m = new MisurazioneMessage("suolo-A1", "parcellaA", "umidita_suolo", 35.0, "%", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }
}