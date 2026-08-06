package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
import it.pegasopw.grapehealth.persistence.repository.NodoSensoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class SchedulerRisoluzioneAllerteTest {

    @Autowired
    private SchedulerRisoluzioneAllerte scheduler;

    @Autowired
    private AllertaRepository allertaRepository;

    @Autowired
    private NodoSensoreRepository nodoSensoreRepository;

    private Long unNodoIdValido() {
        return nodoSensoreRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nessun nodo in nodo_sensore: eseguire init_nodi_db.py prima di questo test"))
                .getId();
    }

    @Test
    @Transactional
    void risolveUnAllertaConScadenzaGiaSuperata() {
        AllertaEntity allerta = allertaRepository.save(new AllertaEntity(
                "stress_idrico", "moderato", unNodoIdValido(), "allerta di test scheduler",
                "stress_idrico", Instant.now()));

        scheduler.pianificaAllaScadenza(allerta.getId(), Instant.now().minusSeconds(1));
        scheduler.risolviScadute();

        AllertaEntity aggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("risolta", aggiornata.getStato());
        assertNotNull(aggiornata.getRisoltaIl());
    }

    @Test
    @Transactional
    void nonRisolveUnAllertaConScadenzaFutura() {
        AllertaEntity allerta = allertaRepository.save(new AllertaEntity(
                "stress_idrico", "moderato", unNodoIdValido(), "allerta di test scheduler futura",
                "stress_idrico", Instant.now()));

        scheduler.pianificaAllaScadenza(allerta.getId(), Instant.now().plusSeconds(600));
        scheduler.risolviScadute();

        AllertaEntity nonAggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("attiva", nonAggiornata.getStato());
        assertNull(nonAggiornata.getRisoltaIl());
    }
}