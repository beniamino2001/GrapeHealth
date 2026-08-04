package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

public final class MisurazioneSpecifications {

    private MisurazioneSpecifications() {
    }

    public static Specification<MisurazioneEntity> nodoIdIn(List<Long> nodoIds) {
        if (nodoIds == null || nodoIds.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> root.get("nodoId").in(nodoIds);
    }

    public static Specification<MisurazioneEntity> parametro(String parametro) {
        if (parametro == null || parametro.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("parametro"), parametro);
    }

    public static Specification<MisurazioneEntity> rilevatoIlDopo(Instant dal) {
        if (dal == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("rilevatoIl"), dal);
    }

    public static Specification<MisurazioneEntity> rilevatoIlPrima(Instant al) {
        if (al == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("rilevatoIl"), al);
    }
}