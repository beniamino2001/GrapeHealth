package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MisurazioneMapperTest {

    @Test
    void risolveParcellaPassandoDalNodoADifferenzaDiAllertaMapper() {
        MisurazioneEntity misurazione = new MisurazioneEntity();
        ReflectionTestUtils.setField(misurazione, "nodoId", 5L);

        NodoSensoreEntity nodo = new NodoSensoreEntity();
        ReflectionTestUtils.setField(nodo, "codice", "meteo-A1");
        ReflectionTestUtils.setField(nodo, "parcellaId", 1L);

        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerId(5L)).thenReturn(nodo);
        when(cacheParcelle.nomePerId(1L)).thenReturn("parcellaA");

        MisurazioneDTO dto = MisurazioneMapper.toDTO(misurazione, cacheNodi, cacheParcelle);

        assertEquals("meteo-A1", dto.nodoCodice());
        assertEquals("parcellaA", dto.parcella());
    }

    @Test
    void nodoNonTrovatoLasciaParcellaNull() {
        MisurazioneEntity misurazione = new MisurazioneEntity();
        ReflectionTestUtils.setField(misurazione, "nodoId", 999L);

        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerId(999L)).thenReturn(null);

        MisurazioneDTO dto = MisurazioneMapper.toDTO(misurazione, cacheNodi, cacheParcelle);

        assertNull(dto.nodoCodice());
        assertNull(dto.parcella());
    }
}