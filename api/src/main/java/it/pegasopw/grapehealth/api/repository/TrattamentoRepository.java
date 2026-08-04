package it.pegasopw.grapehealth.api.repository;

import it.pegasopw.grapehealth.api.model.entity.TrattamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrattamentoRepository extends JpaRepository<TrattamentoEntity, Long> {

    Optional<TrattamentoEntity> findFirstByAllertaId(Long allertaId);

    List<TrattamentoEntity> findByAllertaIdIn(List<Long> allertaIds);
}