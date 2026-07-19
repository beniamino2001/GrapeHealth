package it.pegasopw.grapehealth.persistence.repository;

import it.pegasopw.grapehealth.persistence.model.entity.NodoSensoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodoSensoreRepository extends JpaRepository<NodoSensoreEntity, Long> {
}