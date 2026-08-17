package it.pegasopw.grapehealth.api.repository.spec;

import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class MisurazioneSpecificationsTest {

    private final Root<MisurazioneEntity> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    @Test
    void nodoIdInVuotoNonFiltraNulla() {
        Specification<MisurazioneEntity> spec = MisurazioneSpecifications.nodoIdIn(List.of());
        spec.toPredicate(root, query, cb);
        verify(cb).conjunction();
    }

    @Test
    void nodoIdInValorizzatoFiltraSuiNodiIndicati() {
        Path<Object> nodoIdPath = mock(Path.class);
        Predicate predicatoIn = mock(Predicate.class);
        when(root.<Object>get("nodoId")).thenReturn(nodoIdPath);
        when(nodoIdPath.in(List.of(1L, 2L))).thenReturn(predicatoIn);

        Specification<MisurazioneEntity> spec = MisurazioneSpecifications.nodoIdIn(List.of(1L, 2L));
        Predicate risultato = spec.toPredicate(root, query, cb);

        assertSame(predicatoIn, risultato);
    }

    @Test
    void rilevatoIlDopoUsaGreaterThanOrEqualTo() {
        Path<Instant> rilevatoIlPath = mock(Path.class);
        when(root.<Instant>get("rilevatoIl")).thenReturn(rilevatoIlPath);
        Instant dal = Instant.parse("2026-08-01T00:00:00Z");

        Specification<MisurazioneEntity> spec = MisurazioneSpecifications.rilevatoIlDopo(dal);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(rilevatoIlPath, dal);
    }

    @Test
    void rilevatoIlPrimaUsaLessThanOrEqualTo() {
        Path<Instant> rilevatoIlPath = mock(Path.class);
        when(root.<Instant>get("rilevatoIl")).thenReturn(rilevatoIlPath);
        Instant al = Instant.parse("2026-08-01T00:00:00Z");

        Specification<MisurazioneEntity> spec = MisurazioneSpecifications.rilevatoIlPrima(al);
        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(rilevatoIlPath, al);
    }
}