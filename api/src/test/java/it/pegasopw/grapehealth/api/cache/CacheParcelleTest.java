package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.api.repository.ParcellaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheParcelleTest {

    private ParcellaEntity parcella(Long id, String nome, String varieta, String coloreBacca, Double lunghezzaGermoglioCm) {
        ParcellaEntity entity = new ParcellaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "nome", nome);
        ReflectionTestUtils.setField(entity, "varieta", varieta);
        ReflectionTestUtils.setField(entity, "coloreBacca", coloreBacca);
        ReflectionTestUtils.setField(entity, "lunghezzaGermoglioCm", lunghezzaGermoglioCm);
        return entity;
    }

    @Test
    void restituisceTutteLeParcelleCaricate() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                parcella(1L, "parcellaA", "Sangiovese", "nero", 12.0),
                parcella(2L, "parcellaC", "Trebbiano", "bianco", 10.0)));

        CacheParcelle cache = new CacheParcelle(repository);
        cache.carica();

        assertEquals(2, cache.tutte().size());
    }

    @Test
    void trovaPerNomeRestituisceLEntitaCompletaConIDatiAgronomici() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaA", "Sangiovese", "nero", 12.0)));

        CacheParcelle cache = new CacheParcelle(repository);
        cache.carica();

        ParcellaEntity trovata = cache.trovaPerNome("parcellaA");
        assertNotNull(trovata);
        assertEquals("Sangiovese", trovata.getVarieta());
        assertEquals(12.0, trovata.getLunghezzaGermoglioCm());
    }

    @Test
    void trovaPerNomeRestituisceNullPerNomeSconosciuto() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaA", "Sangiovese", "nero", 12.0)));

        CacheParcelle cache = new CacheParcelle(repository);
        cache.carica();

        assertNull(cache.trovaPerNome("parcellaZ"));
    }
}