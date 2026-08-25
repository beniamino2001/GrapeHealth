package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.model.dto.NodoDTO;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodiServiceTest {

    private NodoSensoreEntity nodo(String codice, Long parcellaId, boolean attivo, LocalDate dataInstallazione) {
        NodoSensoreEntity entity = new NodoSensoreEntity();
        ReflectionTestUtils.setField(entity, "codice", codice);
        ReflectionTestUtils.setField(entity, "parcellaId", parcellaId);
        ReflectionTestUtils.setField(entity, "tipoNodo", "meteo");
        ReflectionTestUtils.setField(entity, "attivo", attivo);
        ReflectionTestUtils.setField(entity, "dataInstallazione", dataInstallazione);
        return entity;
    }

    @Test
    void tuttiMappaOgniEntitaInDTOConLaParcellaRisolta() {
        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.tutti()).thenReturn(List.of(nodo("meteo-A1", 1L, true, LocalDate.of(2026, 1, 10))));
        when(cacheParcelle.nomePerId(1L)).thenReturn("parcellaA");

        List<NodoDTO> risultato = new NodiService(cacheNodi, cacheParcelle).tutti();

        assertEquals(1, risultato.size());
        assertEquals("parcellaA", risultato.get(0).parcella());
        assertTrue(risultato.get(0).attivo());
    }

    @Test
    void perCodiceLanciaRisorsaNonTrovataSeAssente() {
        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerCodice("sconosciuto")).thenReturn(null);

        assertThrows(RisorsaNonTrovataException.class,
                () -> new NodiService(cacheNodi, cacheParcelle).perCodice("sconosciuto"));
    }

    @Test
    void perCodiceRestituisceStatoENonSoloAnagrafica() {
        CacheNodi cacheNodi = mock(CacheNodi.class);
        CacheParcelle cacheParcelle = mock(CacheParcelle.class);
        when(cacheNodi.trovaPerCodice("suolo-A1")).thenReturn(nodo("suolo-A1", 1L, false, LocalDate.of(2026, 3, 1)));
        when(cacheParcelle.nomePerId(1L)).thenReturn("parcellaA");

        NodoDTO dto = new NodiService(cacheNodi, cacheParcelle).perCodice("suolo-A1");

        assertFalse(dto.attivo());
        assertEquals(LocalDate.of(2026, 3, 1), dto.dataInstallazione());
    }
}