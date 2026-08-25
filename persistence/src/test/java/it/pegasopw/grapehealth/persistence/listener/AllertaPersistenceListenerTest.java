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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AllertaPersistenceListenerTest {

    private static final String CODICE_NODO_DI_TEST = "idrico-A1";
    private static final String NOME_PARCELLA_DI_TEST = "parcellaA";

    @Autowired
    private AllertaPersistenceListener listener;

    @Autowired
    private AllertaRepository allertaRepository;

    @Autowired
    private TrattamentoRepository trattamentoRepository;

    private AllertaEvent evento(String tipo, String livello) {
        return new AllertaEvent(tipo, livello, CODICE_NODO_DI_TEST, NOME_PARCELLA_DI_TEST,
                "psi_stem", -1.35, "messaggio di test integrazione", Instant.now());
    }

    @Test
    @Transactional
    void persisteAllertaETrattamentoCollegati() {
        AllertaEvent evento = evento("stress_idrico", "severo");

        listener.onAllerta(evento);

        List<AllertaEntity> allerte = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione())
                        && "stress_idrico".equals(a.getTipo()))
                .toList();
        assertEquals(1, allerte.size(), "doveva essere stata scritta esattamente un'allerta di test");

        AllertaEntity allertaSalvata = allerte.get(0);
        assertNotNull(allertaSalvata.getId());
        assertEquals("stress_idrico", allertaSalvata.getTipo());
        assertEquals("stress_idrico", allertaSalvata.getRegolaCodice());
        assertEquals("severo", allertaSalvata.getLivelloRischio());
        assertNotNull(allertaSalvata.getParcellaId(), "parcellaA deve risolvere a un id valido dal seed");
        assertEquals("attiva", allertaSalvata.getStato());
        assertNull(allertaSalvata.getRisoltaIl());
        assertNotNull(allertaSalvata.getRisoluzionePianificataIl(), "la scadenza deve essere già pianificata e persistita");

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
    void allertaSenzaAzioneCatalogataVienePersistitaSenzaTrattamento() {
        AllertaEvent evento = evento("infezione_secondaria", "moderato");

        listener.onAllerta(evento);

        List<AllertaEntity> allerte = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione())
                        && "infezione_secondaria".equals(a.getTipo()))
                .toList();
        assertEquals(1, allerte.size(), "l'allerta va comunque persistita anche senza un'azione catalogata");

        AllertaEntity allertaSalvata = allerte.get(0);
        assertEquals("attiva", allertaSalvata.getStato());
        assertNotNull(allertaSalvata.getRisoluzionePianificataIl(),
                "va comunque pianificata per la risoluzione, pur senza un trattamento collegato");

        List<TrattamentoEntity> trattamenti = trattamentoRepository.findAll().stream()
                .filter(t -> allertaSalvata.getId().equals(t.getAllertaId()))
                .toList();
        assertEquals(0, trattamenti.size(), "nessun trattamento catalogato per infezione_secondaria");
    }

    @Test
    @Transactional
    void nodoSconosciutoNonScriveNeAllertaNeTrattamento() {
        long allerteIniziali = allertaRepository.count();
        long trattamentiIniziali = trattamentoRepository.count();

        AllertaEvent evento = new AllertaEvent("stress_idrico", "moderato", "nodo-inesistente-xyz",
                NOME_PARCELLA_DI_TEST, "psi_stem", -1.25, "non deve essere scritto", Instant.now());

        listener.onAllerta(evento);

        assertEquals(allerteIniziali, allertaRepository.count());
        assertEquals(trattamentiIniziali, trattamentoRepository.count());
    }

    @Test
    @Transactional
    void parcellaSconosciutaNonBloccaLaScrittura() {
        AllertaEvent evento = new AllertaEvent("stress_idrico", "moderato", CODICE_NODO_DI_TEST,
                "parcella-inesistente-xyz", "psi_stem", -1.25, "test parcella sconosciuta", Instant.now());

        listener.onAllerta(evento);

        List<AllertaEntity> allerte = allertaRepository.findAll().stream()
                .filter(a -> "test parcella sconosciuta".equals(a.getDescrizione()))
                .toList();
        assertEquals(1, allerte.size());
        assertNull(allerte.get(0).getParcellaId());
    }
}