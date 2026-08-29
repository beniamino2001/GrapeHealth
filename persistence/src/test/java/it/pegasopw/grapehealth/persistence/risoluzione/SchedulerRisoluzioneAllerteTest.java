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

    private AllertaEntity nuovaAllertaDiTest(String descrizione) {
        return allertaRepository.save(new AllertaEntity(
                "stress_idrico", "moderato", unNodoIdValido(), null,
                descrizione, "stress_idrico", Instant.now()));
    }

    @Test
    @Transactional
    void risolveUnAllertaConScadenzaGiaSuperata() {
        AllertaEntity allerta = nuovaAllertaDiTest("allerta di test scheduler");

        scheduler.pianificaAllaScadenza(allerta, Instant.now().minusSeconds(1));
        scheduler.risolviScadute();

        AllertaEntity aggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("risolta", aggiornata.getStato());
        assertNotNull(aggiornata.getRisoltaIl());
        assertNull(aggiornata.getRisoluzionePianificataIl(), "non più pendente dopo la risoluzione");
    }

    @Test
    @Transactional
    void nonRisolveUnAllertaConScadenzaFutura() {
        AllertaEntity allerta = nuovaAllertaDiTest("allerta di test scheduler futura");

        scheduler.pianificaAllaScadenza(allerta, Instant.now().plusSeconds(600));
        scheduler.risolviScadute();

        AllertaEntity nonAggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("attiva", nonAggiornata.getStato());
        assertNull(nonAggiornata.getRisoltaIl());
    }

    @Test
    @Transactional
    void pianificaAllaScadenzaPersisteLaScadenzaSullEntita() {
        AllertaEntity allerta = nuovaAllertaDiTest("allerta di test persistenza scadenza");
        Instant scadenza = Instant.now().plusSeconds(120);

        scheduler.pianificaAllaScadenza(allerta, scadenza);

        AllertaEntity ricaricata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals(scadenza, ricaricata.getRisoluzionePianificataIl());
    }

    @Test
    @Transactional
    void ricostruisceLePianificazioniPendentiAllAvvio() {
        // Simula una scadenza già pianificata e persistita da una sessione
        // precedente del processo (stato ancora "attiva"), senza passare da
        // pianifica(...)/pianificaAllaScadenza(...) di QUESTA istanza dello
        // scheduler, per non aggiungerla già alla sua mappa in memoria.
        AllertaEntity allerta = nuovaAllertaDiTest("allerta pendente da sessione precedente");
        Instant scadenzaGiaSuperata = Instant.now().minusSeconds(5);
        allerta.pianificaRisoluzione(scadenzaGiaSuperata);
        allertaRepository.save(allerta);

        scheduler.ricostruisciAllAvvio();
        scheduler.risolviScadute();

        AllertaEntity aggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("risolta", aggiornata.getStato(),
                "la pianificazione doveva essere ricostruita dal DB e poi risolta dallo sweep");
    }

    @Test
    @Transactional
    void risolviOraRisolveSubitoSenzaAttendereLaScadenzaPianificata() {
        AllertaEntity allerta = nuovaAllertaDiTest("allerta di test risoluzione immediata");
        scheduler.pianificaAllaScadenza(allerta, Instant.now().plusSeconds(600));

        scheduler.risolviOra(allerta);

        AllertaEntity aggiornata = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals("risolta", aggiornata.getStato());
        assertNotNull(aggiornata.getRisoltaIl());
    }

    @Test
    @Transactional
    void risolviOraRimuoveLaScadenzaPendenteEvitandoUnaSecondaRisoluzioneDalloSweep() {
        AllertaEntity allerta = nuovaAllertaDiTest("allerta di test doppia risoluzione");
        scheduler.pianificaAllaScadenza(allerta, Instant.now().plusSeconds(600));

        scheduler.risolviOra(allerta);
        Instant risoltaIlDopoRisolviOra = allertaRepository.findById(allerta.getId()).orElseThrow().getRisoltaIl();

        // Anche se la scadenza pianificata (in memoria) fosse rimasta,
        // un successivo sweep non deve sovrascrivere risolta_il: qui si
        // verifica che risolviScadute() non trovi più nulla da fare per
        // questa allerta, perché risolviOra() ha già ripulito la mappa.
        scheduler.risolviScadute();

        AllertaEntity finale = allertaRepository.findById(allerta.getId()).orElseThrow();
        assertEquals(risoltaIlDopoRisolviOra, finale.getRisoltaIl(),
                "risolta_il non deve cambiare a un successivo sweep dopo una risoluzione immediata");
    }
}