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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @Transactional
    void unaSecondaAllertaConStessoTipoLivelloENodoNonDuplicaMaEstendeLaPianificazione() {
        AllertaEvent primo = evento("ondata_di_calore", "moderato");
        listener.onAllerta(primo);

        List<AllertaEntity> dopoPrimo = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione())
                        && "ondata_di_calore".equals(a.getTipo()))
                .toList();
        assertEquals(1, dopoPrimo.size());
        AllertaEntity allertaIniziale = dopoPrimo.get(0);
        Instant scadenzaIniziale = allertaIniziale.getRisoluzionePianificataIl();
        assertNotNull(scadenzaIniziale);

        // Un secondo evento identico, poco dopo: non deve creare una seconda riga,
        // deve solo estendere la pianificazione di risoluzione di quella esistente.
        AllertaEvent secondo = evento("ondata_di_calore", "moderato");
        listener.onAllerta(secondo);

        List<AllertaEntity> dopoSecondo = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione())
                        && "ondata_di_calore".equals(a.getTipo()))
                .toList();
        assertEquals(1, dopoSecondo.size(), "il secondo evento non deve aprire una riga duplicata");

        AllertaEntity stessaAllerta = dopoSecondo.get(0);
        assertEquals(allertaIniziale.getId(), stessaAllerta.getId());
        assertFalse(stessaAllerta.getRisoluzionePianificataIl().isBefore(scadenzaIniziale),
                "la pianificazione di risoluzione deve essere estesa, non anticipata");

        List<TrattamentoEntity> trattamenti = trattamentoRepository.findAll().stream()
                .filter(t -> allertaIniziale.getId().equals(t.getAllertaId()))
                .toList();
        assertEquals(1, trattamenti.size(), "un solo trattamento, non uno per ogni ripubblicazione dello stesso rischio");
    }

    @Test
    @Transactional
    void unCambioDiLivelloSulloStessoNodoChiudeSubitoQuellaPrecedente() {
        AllertaEvent moderato = evento("sunburn", "moderato");
        listener.onAllerta(moderato);

        AllertaEvent severo = evento("sunburn", "severo");
        listener.onAllerta(severo);

        List<AllertaEntity> allerte = allertaRepository.findAll().stream()
                .filter(a -> "messaggio di test integrazione".equals(a.getDescrizione())
                        && "sunburn".equals(a.getTipo()))
                .toList();
        assertEquals(2, allerte.size(), "restano due righe: quella superata dal cambio di livello e quella nuova");

        AllertaEntity quellaModerata = allerte.stream()
                .filter(a -> "moderato".equals(a.getLivelloRischio())).findFirst().orElseThrow();
        AllertaEntity quellaSevera = allerte.stream()
                .filter(a -> "severo".equals(a.getLivelloRischio())).findFirst().orElseThrow();

        assertEquals("risolta", quellaModerata.getStato(),
                "il livello precedente va chiuso subito, non lasciato scaduto in parallelo al nuovo");
        assertNotNull(quellaModerata.getRisoltaIl());

        assertEquals("attiva", quellaSevera.getStato());
        assertNull(quellaSevera.getRisoltaIl());
        assertNotNull(quellaSevera.getRisoluzionePianificataIl());

        // Mai due allerte attive in contemporanea sullo stesso nodo/tipo,
        // indipendentemente dal livello - il sintomo osservato in produzione.
        long attive = allerte.stream().filter(a -> "attiva".equals(a.getStato())).count();
        assertEquals(1, attive, "un solo nodo/tipo non può avere due livelli di rischio attivi insieme");
    }
}