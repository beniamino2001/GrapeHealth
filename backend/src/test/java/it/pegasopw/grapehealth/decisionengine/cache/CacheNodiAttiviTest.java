package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.decisionengine.repository.NodoSensoreRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheNodiAttiviTest {

    private NodoSensoreEntity nodo(String codice, boolean attivo) {
        NodoSensoreEntity n = new NodoSensoreEntity();
        n.setCodice(codice);
        n.setAttivo(attivo);
        return n;
    }

    @Test
    void restituisceTruePerUnNodoAttivo() {
        NodoSensoreRepository repo = mock(NodoSensoreRepository.class);
        when(repo.findAll()).thenReturn(List.of(nodo("meteo-A1", true)));
        CacheNodiAttivi cache = new CacheNodiAttivi(repo);
        cache.carica();

        assertEquals(Boolean.TRUE, cache.attivo("meteo-A1"));
    }

    @Test
    void restituisceFalsePerUnNodoDisattivato() {
        NodoSensoreRepository repo = mock(NodoSensoreRepository.class);
        when(repo.findAll()).thenReturn(List.of(nodo("meteo-A1", false)));
        CacheNodiAttivi cache = new CacheNodiAttivi(repo);
        cache.carica();

        assertEquals(Boolean.FALSE, cache.attivo("meteo-A1"));
    }

    @Test
    void restituisceNullPerUnNodoSconosciuto() {
        NodoSensoreRepository repo = mock(NodoSensoreRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        CacheNodiAttivi cache = new CacheNodiAttivi(repo);
        cache.carica();

        assertNull(cache.attivo("meteo-mai-visto"));
    }
}