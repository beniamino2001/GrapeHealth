package it.pegasopw.grapehealth.api.repository;

import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MisurazioneRepository
        extends JpaRepository<MisurazioneEntity, Long>, JpaSpecificationExecutor<MisurazioneEntity> {
}