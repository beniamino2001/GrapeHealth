package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllertaRepository extends JpaRepository<AllertaEntity, Long> {
}