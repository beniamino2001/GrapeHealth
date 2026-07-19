package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.MisurazioneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MisurazioneRepository extends JpaRepository<MisurazioneEntity, Long> {
}