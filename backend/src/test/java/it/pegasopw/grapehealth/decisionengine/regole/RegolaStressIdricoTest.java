package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegolaStressIdricoTest {

    private final RegolaStressIdrico regola = new RegolaStressIdrico(cacheSoglieRegole());

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private CacheSoglieRegole cacheSoglieRegole() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("stress_idrico", "psi_stem", "moderato")).thenReturn(soglia(-1.2));
        when(cache.sogliaUnica("stress_idrico", "psi_stem", "severo")).thenReturn(soglia(-1.4));
        return cache;
    }
    private final StatoRischio stato = new StatoRischio();

    private MisurazioneMessage misurazionePsiStem(double valore) {
        return new MisurazioneMessage("idrico-A1", "parcellaA", "psi_stem", valore, "MPa", Instant.now());
    }

    private MisurazioneMessage misurazioneUmiditaSuolo(String parcella, double valore) {
        return new MisurazioneMessage("suolo-A1", parcella, "umidita_suolo", valore, "%", Instant.now());
    }

    @Test
    void nonScattaSottoSogliaModerato() {
        // -1.19 MPa: appena sopra la soglia di stress moderato, nessuna allerta attesa
        Optional<?> risultato = regola.valuta(misurazionePsiStem(-1.19), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void scattaModeratoAppenaSottoSoglia() {
        // -1.21 MPa: appena sotto -1.2, allerta moderata attesa
        var risultato = regola.valuta(misurazionePsiStem(-1.21), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void restaModeratoAppenaSopraSogliaSevero() {
        // -1.39 MPa: sotto la soglia moderata ma non ancora quella severa
        var risultato = regola.valuta(misurazionePsiStem(-1.39), stato);
        assertTrue(risultato.isPresent());
        assertEquals("moderato", risultato.get().livelloRischio());
    }

    @Test
    void scattaSeveroAppenaSottoSoglia() {
        // -1.41 MPa: sotto la soglia di stress severo
        var risultato = regola.valuta(misurazionePsiStem(-1.41), stato);
        assertTrue(risultato.isPresent());
        assertEquals("severo", risultato.get().livelloRischio());
    }

    @Test
    void ignoraParametriDiversiDaPsiStem() {
        // stesso valore critico, ma su un parametro diverso la regola non deve applicarsi
        var m = new MisurazioneMessage("meteo-A1", "parcellaA", "temperatura_aria", -1.5, "C", Instant.now());
        assertTrue(regola.valuta(m, stato).isEmpty());
    }

    @Test
    void nonRipubblicaSeLivelloRischioInvariato() {
        var m = misurazionePsiStem(-1.25); // moderato

        var primaChiamata = regola.valuta(m, stato);
        assertTrue(primaChiamata.isPresent());

        var secondaChiamata = regola.valuta(m, stato); // stessa condizione, stessa lettura ripetuta
        assertTrue(secondaChiamata.isEmpty());
    }

    @Test
    void ripubblicaQuandoIlRischioSiAggrava() {
        regola.valuta(misurazionePsiStem(-1.25), stato); // moderato: pubblica

        var aggravamento = regola.valuta(misurazionePsiStem(-1.45), stato); // severo: cambia livello
        assertTrue(aggravamento.isPresent());
        assertEquals("severo", aggravamento.get().livelloRischio());
    }

    @Test
    void ripubblicaSeIlRischioRicompareDopoUnRientro() {
        regola.valuta(misurazionePsiStem(-1.25), stato); // moderato: pubblica
        regola.valuta(misurazionePsiStem(-0.9), stato);   // rientro sotto soglia: azzera lo stato

        var nuovoSuperamento = regola.valuta(misurazionePsiStem(-1.25), stato); // ricompare
        assertTrue(nuovoSuperamento.isPresent());
    }

    @Test
    void nonRientraPerOscillazioniPiccoleAttornoAllaSoglia() {
        var primaAllerta = regola.valuta(misurazionePsiStem(-1.23), stato); // moderato: pubblica
        assertTrue(primaAllerta.isPresent());

        // -1.18: sopra la soglia grezza (-1.2) ma dentro il margine di isteresi non deve generare né un rientro né una nuova allerta
        var oscillazione = regola.valuta(misurazionePsiStem(-1.18), stato);
        assertTrue(oscillazione.isEmpty());

        // -1.10: oltre soglia+isteresi (-1.15): qui il rientro è genuino
        var rientroVero = regola.valuta(misurazionePsiStem(-1.10), stato);
        assertTrue(rientroVero.isEmpty());

        // Il rientro non genera un evento, ma una nuova discesa sotto soglia deve ora essere trattata come condizione nuova
        var nuovaAllerta = regola.valuta(misurazionePsiStem(-1.25), stato);
        assertTrue(nuovaAllerta.isPresent());
    }

    @Test
    void tracciaUmiditaSuoloSenzaGenerareAllerta() {
        var risultato = regola.valuta(misurazioneUmiditaSuolo("parcellaA", 18.0), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void arricchisceIlMessaggioConLUmiditaDelSuoloSeGiaNota() {
        regola.valuta(misurazioneUmiditaSuolo("parcellaA", 18.0), stato);

        var risultato = regola.valuta(misurazionePsiStem(-1.25), stato); // moderato
        assertTrue(risultato.isPresent());
        assertTrue(risultato.get().messaggio().contains("umidità del suolo"));
    }

    @Test
    void nonArricchisceIlMessaggioSeLUmiditaDelSuoloNonEAncoraNota() {
        var risultato = regola.valuta(misurazionePsiStem(-1.25), stato); // moderato, nessuna lettura precedente
        assertTrue(risultato.isPresent());
        assertFalse(risultato.get().messaggio().contains("umidità del suolo"));
    }

    @Test
    void nonMescolaLUmiditaDelSuoloTraParcelleDiverse() {
        regola.valuta(misurazioneUmiditaSuolo("parcellaB", 18.0), stato);

        var risultato = regola.valuta(misurazionePsiStem(-1.25), stato); // trigger su parcellaA
        assertTrue(risultato.isPresent());
        assertFalse(risultato.get().messaggio().contains("umidità del suolo"));
    }
}