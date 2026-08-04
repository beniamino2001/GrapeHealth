package it.pegasopw.grapehealth.api.repository;

import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NodoSensoreRepository extends JpaRepository<NodoSensoreEntity, Long> {

    List<NodoSensoreEntity> findByParcella(String parcella);

    Optional<NodoSensoreEntity> findByCodice(String codice);
}