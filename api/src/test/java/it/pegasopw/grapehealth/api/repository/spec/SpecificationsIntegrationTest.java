package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import it.pegasopw.grapehealth.api.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.api.repository.NodoSensoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Verifica i predicati JPA contro un database Postgres reale, non mockato: a differenza del
// resto della suite, richiede lo stack Docker attivo (stesso datasource di application.yaml).
// Le entita' di questo modulo non hanno @GeneratedValue sull'id, perche' api non scrive mai
// dati in produzione: le fixture sono percio' inserite via JdbcTemplate (SQL diretto, id
// assegnato dal DEFAULT di Postgres), non tramite i repository JPA. @Transactional sulla
// classe garantisce il rollback automatico a fine test.
@SpringBootTest
@Transactional
class SpecificationsIntegrationTest {

    @Autowired
    private AllertaRepository allertaRepository;
    @Autowired
    private MisurazioneRepository misurazioneRepository;
    @Autowired
    private NodoSensoreRepository nodoSensoreRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void filtroStatoAllertaRestituisceSoloLeRigheConLoStatoIndicato() {
        long idAttiva = inserisciAllerta("attiva");
        long idRisolta = inserisciAllerta("risolta");

        List<AllertaEntity> risultato = allertaRepository.findAll(AllertaSpecifications.stato("attiva"));

        assertTrue(risultato.stream().allMatch(a -> "attiva".equals(a.getStato())));
        assertTrue(risultato.stream().anyMatch(a -> a.getId() == idAttiva));
        assertTrue(risultato.stream().noneMatch(a -> a.getId() == idRisolta));
    }

    @Test
    void filtroParcellaIdNulloNonEsclude() {
        long id = inserisciAllerta("attiva");

        List<AllertaEntity> risultato = allertaRepository.findAll(AllertaSpecifications.parcellaId(null));

        assertTrue(risultato.stream().anyMatch(a -> a.getId() == id));
    }

    @Test
    void filtroIntervalloTemporaleMisurazioneEscludeCorrettamenteFuoriRange() {
        NodoSensoreEntity nodo = nodoSensoreRepository.findAll().get(0);
        long idDentro = inserisciMisurazione(nodo.getId(), Instant.parse("2026-06-15T12:00:00Z"));
        long idFuori = inserisciMisurazione(nodo.getId(), Instant.parse("2026-01-01T00:00:00Z"));

        Specification<MisurazioneEntity> intervallo = MisurazioneSpecifications
                .rilevatoIlDopo(Instant.parse("2026-06-01T00:00:00Z"))
                .and(MisurazioneSpecifications.rilevatoIlPrima(Instant.parse("2026-06-30T23:59:59Z")));
        List<MisurazioneEntity> risultato = misurazioneRepository.findAll(intervallo);

        assertTrue(risultato.stream().anyMatch(m -> m.getId() == idDentro));
        assertTrue(risultato.stream().noneMatch(m -> m.getId() == idFuori));
    }

    private long inserisciAllerta(String stato) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO allerta (tipo, livello_rischio, descrizione, regola_codice, stato) " +
                        "VALUES ('sunburn', 'moderato', 'Fixture di test', 'sunburn', ?) RETURNING id",
                Long.class, stato);
    }

    private long inserisciMisurazione(Long nodoId, Instant rilevatoIl) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO misurazione (nodo_id, parametro, valore, unita_misura, rilevato_il) " +
                        "VALUES (?, 'temperatura_aria', 30.0, 'C', ?) RETURNING id",
                Long.class, nodoId, Timestamp.from(rilevatoIl));
    }
}