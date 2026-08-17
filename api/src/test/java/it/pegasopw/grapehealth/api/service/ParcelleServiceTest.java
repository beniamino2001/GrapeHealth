package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.model.dto.ParcellaDTO;
import it.pegasopw.grapehealth.api.model.entity.ParcellaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParcelleServiceTest {

    private ParcellaEntity parcella(String nome, String varieta, Double lunghezzaGermoglioCm) {
        ParcellaEntity entity = new ParcellaEntity();
        ReflectionTestUtils.setField(entity, "nome", nome);
        ReflectionTestUtils.setField(entity, "varieta", varieta);
        ReflectionTestUtils.setField(entity, "lunghezzaGermoglioCm", lunghezzaGermoglioCm);
        return entity;
    }

    @Test
    void tutteMappaOgniEntitaInDTO() {
        CacheParcelle cache = mock(CacheParcelle.class);
        when(cache.tutte()).thenReturn(List.of(
                parcella("parcellaA", "Sangiovese", 12.0),
                parcella("parcellaC", "Trebbiano", 10.0)));

        List<ParcellaDTO> risultato = new ParcelleService(cache).tutte();

        assertEquals(2, risultato.size());
        assertTrue(risultato.stream().anyMatch(p -> p.nome().equals("parcellaA") && p.varieta().equals("Sangiovese")));
    }

    @Test
    void perNomeLanciaRisorsaNonTrovataSeAssente() {
        CacheParcelle cache = mock(CacheParcelle.class);
        when(cache.trovaPerNome("parcellaZ")).thenReturn(null);

        RisorsaNonTrovataException ex = assertThrows(RisorsaNonTrovataException.class,
                () -> new ParcelleService(cache).perNome("parcellaZ"));
        assertTrue(ex.getMessage().contains("parcellaZ"));
    }

    @Test
    void perNomeRestituisceIlDTOCompleto() {
        CacheParcelle cache = mock(CacheParcelle.class);
        when(cache.trovaPerNome("parcellaC")).thenReturn(parcella("parcellaC", "Trebbiano", 10.0));

        ParcellaDTO dto = new ParcelleService(cache).perNome("parcellaC");

        assertEquals("Trebbiano", dto.varieta());
        assertEquals(10.0, dto.lunghezzaGermoglioCm());
    }
}