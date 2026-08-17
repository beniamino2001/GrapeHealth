package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class AllertaSpecificationsTest {

    private final Root<AllertaEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    void statoNulloNonFiltraNulla() {
        Specification<AllertaEntity> spec = AllertaSpecifications.stato(null);
        spec.toPredicate(root, query, cb);
        verify(cb).conjunction();
        verifyNoMoreInteractions(cb);
    }

    @Test
    void statoValorizzatoFiltraSulCampoStato() {
        Path<Object> statoPath = mock(Path.class);
        when(root.<Object>get("stato")).thenReturn(statoPath);

        Specification<AllertaEntity> spec = AllertaSpecifications.stato("attiva");
        spec.toPredicate(root, query, cb);

        verify(cb).equal(statoPath, "attiva");
    }

    @Test
    void tipoConSoliSpaziNonFiltraNulla() {
        Specification<AllertaEntity> spec = AllertaSpecifications.tipo("   ");
        spec.toPredicate(root, query, cb);
        verify(cb).conjunction();
    }

    @Test
    void parcellaIdValorizzatoFiltraSulCampoParcellaId() {
        Path<Object> parcellaPath = mock(Path.class);
        when(root.<Object>get("parcellaId")).thenReturn(parcellaPath);

        Specification<AllertaEntity> spec = AllertaSpecifications.parcellaId(3L);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(parcellaPath, 3L);
    }
}