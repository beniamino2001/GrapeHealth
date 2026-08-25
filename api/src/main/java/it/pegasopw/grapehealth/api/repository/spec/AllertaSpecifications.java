package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.springframework.data.jpa.domain.Specification;

// Predicati JPA componibili per il filtro di /api/allerte. Convenzione comune a tutti: un
// valore nullo o vuoto non filtra nulla (cb.conjunction(), predicato sempre vero), cosi' i
// filtri si possono comporre liberamente senza dover controllare a monte quali siano stati
// effettivamente richiesti.
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

    // allerta ha una FK diretta a parcellaId: il filtro si applica senza
    // indirezione tramite il nodo.
    public static Specification<AllertaEntity> parcellaId(Long parcellaId) {
        if (parcellaId == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("parcellaId"), parcellaId);
    }
}