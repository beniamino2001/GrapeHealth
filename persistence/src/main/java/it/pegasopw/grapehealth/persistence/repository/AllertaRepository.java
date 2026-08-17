package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllertaRepository extends JpaRepository<AllertaEntity, Long> {

    // Usata da SchedulerRisoluzioneAllerte all'avvio per ricostruire le
    // pianificazioni pendenti: tutte le allerte ancora attive che hanno una
    // scadenza di risoluzione già pianificata da una sessione precedente.
    List<AllertaEntity> findByStatoAndRisoluzionePianificataIlNotNull(String stato);
}