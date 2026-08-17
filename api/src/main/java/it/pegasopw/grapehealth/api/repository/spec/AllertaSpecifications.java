package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.springframework.data.jpa.domain.Specification;

public final class AllertaSpecifications {

    private AllertaSpecifications() {
    }

    public static Specification<AllertaEntity> stato(String stato) {
        if (stato == null || stato.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("stato"), stato);
    }

    public static Specification<AllertaEntity> tipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
    }

    public static Specification<AllertaEntity> parcellaId(Long parcellaId) {
        if (parcellaId == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("parcellaId"), parcellaId);
    }
}