package it.pegasopw.grapehealth.api.repository;

import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

// Nessuna query custom: CacheNodi/CacheParcelle caricano l'intero contenuto
// una sola volta all'avvio (12 nodi su quattro tipi, 3 parcelle, fisse per
// l'intera sessione simulata).
public interface NodoSensoreRepository extends JpaRepository<NodoSensoreEntity, Long> {
}