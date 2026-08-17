package it.pegasopw.grapehealth.persistence.cache;

import it.pegasopw.grapehealth.persistence.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.persistence.repository.ParcellaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheParcelleTest {

    private ParcellaEntity parcella(Long id, String nome) {
        ParcellaEntity entity = new ParcellaEntity();
        entity.setId(id);
        entity.setNome(nome);
        return entity;
    }

    @Test
    void risolveIdPerNomeNotoDopoIlCaricamento() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaA"), parcella(2L, "parcellaB")));

        CacheParcelle cache = new CacheParcelle(repository);
        cache.carica();

        assertEquals(1L, cache.idPerNome("parcellaA"));
        assertEquals(2L, cache.idPerNome("parcellaB"));
    }

    @Test
    void restituisceNullPerNomeSconosciuto() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaA")));

        CacheParcelle cache = new CacheParcelle(repository);
        cache.carica();

        assertNull(cache.idPerNome("parcella-inesistente"));
    }
}