package it.pegasopw.grapehealth.persistence.azione;

import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
        assertEquals("irrigazione_soccorso", mappatore.tipoAzione(evento("stress_idrico", "moderato")));
    }

    @Test
    void ondataDiCaloreMappaSuNebulizzazione() {
        assertEquals("nebulizzazione", mappatore.tipoAzione(evento("ondata_di_calore", "moderato")));
    }

    @Test
    void sunburnMappaSuNebulizzazione() {
        assertEquals("nebulizzazione", mappatore.tipoAzione(evento("sunburn", "severo")));
    }

    @Test
    void treDieciMappaSuTrattamentoFitosanitario() {
        assertEquals("trattamento_fitosanitario", mappatore.tipoAzione(evento("tre_dieci", "moderato")));
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
}