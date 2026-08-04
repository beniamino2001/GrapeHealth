package it.pegasopw.grapehealth.api.repository;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AllertaRepository
        extends JpaRepository<AllertaEntity, Long>, JpaSpecificationExecutor<AllertaEntity> {
}