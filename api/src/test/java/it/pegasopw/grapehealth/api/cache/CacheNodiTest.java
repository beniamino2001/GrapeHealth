package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.NodoSensoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheNodiTest {

    private NodoSensoreEntity nodo(Long id, String codice, Long parcellaId) {
        NodoSensoreEntity entity = new NodoSensoreEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "codice", codice);
        ReflectionTestUtils.setField(entity, "parcellaId", parcellaId);
        return entity;
    }

    @Test
    void trovaPerIdRestituisceIlNodoCaricato() {
        NodoSensoreRepository repository = mock(NodoSensoreRepository.class);
        when(repository.findAll()).thenReturn(List.of(nodo(5L, "meteo-A1", 1L)));

        CacheNodi cache = new CacheNodi(repository);
        cache.carica();

        assertEquals("meteo-A1", cache.trovaPerId(5L).getCodice());
    }

    @Test
    void trovaPerIdRestituisceNullSeIdNonPassatoOSconosciuto() {
        NodoSensoreRepository repository = mock(NodoSensoreRepository.class);
        when(repository.findAll()).thenReturn(List.of());

        CacheNodi cache = new CacheNodi(repository);
        cache.carica();

        assertNull(cache.trovaPerId(null));
        assertNull(cache.trovaPerId(999L));
    }

    @Test
    void nodoIdsPerParcellaRaggruppaCorrettamentePerParcellaId() {
        NodoSensoreRepository repository = mock(NodoSensoreRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                nodo(5L, "meteo-A1", 1L),
                nodo(6L, "idrico-A1", 1L),
                nodo(7L, "meteo-B1", 2L)));

        CacheNodi cache = new CacheNodi(repository);
        cache.carica();

        assertEquals(List.of(5L, 6L), cache.nodoIdsPerParcella(1L));
        assertEquals(List.of(7L), cache.nodoIdsPerParcella(2L));
        assertTrue(cache.nodoIdsPerParcella(999L).isEmpty());
    }
}