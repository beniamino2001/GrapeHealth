package it.pegasopw.grapehealth.persistence.azione;

import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappatoreAzioneTest {

    private final MappatoreAzione mappatore = new MappatoreAzione();

    private AllertaEvent evento(String tipo, String livello) {
        return new AllertaEvent(tipo, livello, "nodo-test", "parcellaA",
                "parametro-test", -1.3, "messaggio di test", Instant.now());
    }

    @Test
    void stressIdricoMappaSuIrrigazioneSoccorso() {
        assertEquals(Optional.of("irrigazione_soccorso"), mappatore.tipoAzione(evento("stress_idrico", "moderato")));
    }

    @Test
    void ondataDiCaloreMappaSuNebulizzazione() {
        assertEquals(Optional.of("nebulizzazione"), mappatore.tipoAzione(evento("ondata_di_calore", "moderato")));
    }

    @Test
    void sunburnMappaSuNebulizzazione() {
        assertEquals(Optional.of("nebulizzazione"), mappatore.tipoAzione(evento("sunburn", "severo")));
    }

    @Test
    void treDieciMappaSuTrattamentoFitosanitario() {
        assertEquals(Optional.of("trattamento_fitosanitario"), mappatore.tipoAzione(evento("tre_dieci", "moderato")));
    }

    @Test
    void svernamentoOosporeNonHaAzioneCatalogata() {
        assertEquals(Optional.empty(), mappatore.tipoAzione(evento("svernamento_oospore", "moderato")));
    }

    @Test
    void infezioneSecondariaNonHaAzioneCatalogata() {
        assertEquals(Optional.empty(), mappatore.tipoAzione(evento("infezione_secondaria", "moderato")));
    }

    @Test
    void tipoSconosciutoLanciaEccezione() {
        assertThrows(IllegalArgumentException.class, () -> mappatore.tipoAzione(evento("tipo_inesistente", "moderato")));
    }

    @Test
    void noteContieneIDettagliDellEvento() {
        AllertaEvent evento = evento("sunburn", "severo");
        String note = mappatore.note(evento);

        assertTrue(note.contains("sunburn"));
        assertTrue(note.contains("severo"));
        assertTrue(note.contains("parametro-test"));
        assertTrue(note.contains("messaggio di test"));
    }

    @Test
    void valoreOsservatoUsaSempreIlPuntoComeSeparatoreDecimaleAPrescindereDalLocaleDiDefault() {
        Locale localeOriginale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ITALY);
            String note = mappatore.note(evento("stress_idrico", "severo"));

            assertTrue(note.contains("valoreOsservato=-1.30"),
                    "atteso il punto come separatore decimale anche con Locale.ITALY come default: " + note);
        } finally {
            Locale.setDefault(localeOriginale);
        }
    }

    @Test
    void dannoRadicaleNonHaAzioneCatalogata() {
        assertEquals(Optional.empty(), mappatore.tipoAzione(evento("danno_radicale", "severo")));
    }
}