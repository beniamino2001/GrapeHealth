package it.pegasopw.grapehealth.api.service.support;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodoResolverTest {

    private final CacheParcelle cacheParcelle = mock(CacheParcelle.class);
    private final CacheNodi cacheNodi = mock(CacheNodi.class);
    private final NodoResolver resolver = new NodoResolver(cacheParcelle, cacheNodi);

    @Test
    void nomeNulloRestituisceNullNessunFiltroApplicato() {
        assertNull(resolver.nodoIdsPerParcella(null));
    }

    @Test
    void nomeVuotoRestituisceNullNessunFiltroApplicato() {
        assertNull(resolver.nodoIdsPerParcella("   "));
    }

    @Test
    void parcellaSconosciutaRestituisceListaVuota() {
        when(cacheParcelle.idPerNome("parcellaZ")).thenReturn(null);
        assertTrue(resolver.nodoIdsPerParcella("parcellaZ").isEmpty());
    }

    @Test
    void parcellaNotaRisolveIdNodiDallaCacheNodi() {
        when(cacheParcelle.idPerNome("parcellaA")).thenReturn(1L);
        when(cacheNodi.nodoIdsPerParcella(1L)).thenReturn(List.of(5L, 6L, 7L));

        assertEquals(List.of(5L, 6L, 7L), resolver.nodoIdsPerParcella("parcellaA"));
    }
}