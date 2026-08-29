package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllertaRepository extends JpaRepository<AllertaEntity, Long> {

    // Usata da SchedulerRisoluzioneAllerte all'avvio per ricostruire le
    // pianificazioni pendenti: tutte le allerte ancora attive che hanno una
    // scadenza di risoluzione già pianificata da una sessione precedente.
    List<AllertaEntity> findByStatoAndRisoluzionePianificataIlNotNull(String stato);

    // Usata da AllertaPersistenceListener per riconoscere se esiste già
    // un'allerta attiva per lo stesso nodo/tipo, a prescindere dal livello:
    // il confronto tra il livello esistente e quello del nuovo evento decide
    // se si tratta della stessa condizione ancora in corso o di un cambio di
    // livello da chiudere e riaprire.
    Optional<AllertaEntity> findByNodoIdAndTipoAndStato(Long nodoId, String tipo, String stato);
}