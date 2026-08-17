package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllertaMapperTest {

    @Test
    void risolveCodiceNodoENomeParcellaDalleCache() {
        AllertaEntity allerta = new AllertaEntity();
        ReflectionTestUtils.setField(allerta, "nodoId", 5L);
        ReflectionTestUtils.setField(allerta, "parcellaId", 1L);

        NodoSensoreEntity nodo = new NodoSensoreEntity();
        ReflectionTestUtils.setField(nodo, "codice", "meteo-A1");

        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerId(5L)).thenReturn(nodo);
        when(cacheParcelle.nomePerId(1L)).thenReturn("parcellaA");

        AllertaDTO dto = AllertaMapper.toDTO(allerta, cacheNodi, cacheParcelle);

        assertEquals("meteo-A1", dto.nodoCodice());
        assertEquals("parcellaA", dto.parcella());
    }

    @Test
    void nodoNonTrovatoLasciaCodiceNodoNullSenzaFarFallireLaMappatura() {
        AllertaEntity allerta = new AllertaEntity();
        ReflectionTestUtils.setField(allerta, "nodoId", 999L);
        ReflectionTestUtils.setField(allerta, "parcellaId", 1L);

        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerId(999L)).thenReturn(null);
        when(cacheParcelle.nomePerId(1L)).thenReturn("parcellaA");

        AllertaDTO dto = AllertaMapper.toDTO(allerta, cacheNodi, cacheParcelle);

        assertNull(dto.nodoCodice());
        assertEquals("parcellaA", dto.parcella());
    }
}