package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.TrattamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrattamentoRepository extends JpaRepository<TrattamentoEntity, Long> {
}