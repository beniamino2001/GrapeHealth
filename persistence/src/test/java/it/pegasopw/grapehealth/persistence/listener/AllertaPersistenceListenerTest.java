package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.persistence.model.entity.TrattamentoEntity;
import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
import it.pegasopw.grapehealth.persistence.repository.TrattamentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AllertaPersistenceListenerTest {

    private static final String CODICE_NODO_DI_TEST = "idrico-A1";

    @Autowired
    private AllertaPersistenceListener listener;

    @Autowired
    private AllertaRepository allertaRepository;

    @Autowired
    private TrattamentoRepository trattamentoRepository;

    private AllertaEvent evento(String tipo, String livello) {
        return new AllertaEvent(tipo, livello, CODICE_NODO_DI_TEST, "parcellaA",
                "psi_stem", -1.35, "messaggio di test integrazione", Instant.now());
    }

    @Test
    @Transactional
    void persisteAllertaETrattamentoCollegati() {
        AllertaEvent evento = evento("stress_idrico", "severo");

        listener.onAllerta(evento);

        List<AllertaEntity> allerte = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione()))
                .toList();
        assertEquals(1, allerte.size(), "doveva essere stata scritta esattamente un'allerta di test");

        AllertaEntity allertaSalvata = allerte.get(0);
        assertNotNull(allertaSalvata.getId());
        assertEquals("stress_idrico", allertaSalvata.getTipo());
        assertEquals("severo", allertaSalvata.getLivelloRischio());

        List<TrattamentoEntity> trattamenti = trattamentoRepository.findAll().stream()
                .filter(t -> allertaSalvata.getId().equals(t.getAllertaId()))
                .toList();
        assertEquals(1, trattamenti.size(), "doveva esistere esattamente un trattamento collegato a quell'allerta");

        TrattamentoEntity trattamentoSalvato = trattamenti.get(0);
        assertEquals("irrigazione_soccorso", trattamentoSalvato.getTipoAzione());
        assertTrue(trattamentoSalvato.getNote().contains("stress_idrico"));
        assertTrue(trattamentoSalvato.getNote().contains("severo"));
    }

    @Test
    @Transactional
    void nodoSconosciutoNonScriveNeAllertaNeTrattamento() {
        long allerteIniziali = allertaRepository.count();
        long trattamentiIniziali = trattamentoRepository.count();

        AllertaEvent evento = new AllertaEvent("stress_idrico", "moderato", "nodo-inesistente-xyz",
                "parcellaA", "psi_stem", -1.25, "non deve essere scritto", Instant.now());

        listener.onAllerta(evento);

        assertEquals(allerteIniziali, allertaRepository.count());
        assertEquals(trattamentiIniziali, trattamentoRepository.count());
    }
}