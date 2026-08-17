package it.pegasopw.grapehealth.persistence.cache;

import it.pegasopw.grapehealth.persistence.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.persistence.repository.NodoSensoreRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheNodiTest {

    private NodoSensoreEntity nodo(Long id, String codice) {
        NodoSensoreEntity entity = new NodoSensoreEntity();
        entity.setId(id);
        entity.setCodice(codice);
        entity.setTipoNodo("idrico");
        return entity;
    }

    @Test
    void risolveIdPerCodiceNotoDopoIlCaricamento() {
        NodoSensoreRepository repository = mock(NodoSensoreRepository.class);
        when(repository.findAll()).thenReturn(List.of(nodo(1L, "idrico-A1"), nodo(2L, "idrico-B1")));

        CacheNodi cache = new CacheNodi(repository);
        cache.carica();

        assertEquals(1L, cache.idPerCodice("idrico-A1"));
        assertEquals(2L, cache.idPerCodice("idrico-B1"));
    }

    @Test
    void restituisceNullPerCodiceSconosciuto() {
        NodoSensoreRepository repository = mock(NodoSensoreRepository.class);
        when(repository.findAll()).thenReturn(List.of(nodo(1L, "idrico-A1")));

        CacheNodi cache = new CacheNodi(repository);
        cache.carica();

        assertNull(cache.idPerCodice("nodo-inesistente"));
    }
}