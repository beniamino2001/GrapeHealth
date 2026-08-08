package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.decisionengine.repository.ParcellaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheGermogliTest {

    private ParcellaEntity parcella(Long id, String nome, Double lunghezzaCm) {
        ParcellaEntity entity = new ParcellaEntity();
        entity.setId(id);
        entity.setNome(nome);
        entity.setLunghezzaGermoglioCm(lunghezzaCm);
        return entity;
    }

    @Test
    void risolveLunghezzaPerParcellaNotaDopoIlCaricamento() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                parcella(1L, "parcellaA", 12.0),
                parcella(2L, "parcellaB", 14.0)));

        CacheGermogli cache = new CacheGermogli(repository);
        cache.carica();

        assertEquals(12.0, cache.lunghezzaCm("parcellaA"));
        assertEquals(14.0, cache.lunghezzaCm("parcellaB"));
    }

    @Test
    void restituisceZeroPerParcellaSconosciuta() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaA", 12.0)));

        CacheGermogli cache = new CacheGermogli(repository);
        cache.carica();

        assertEquals(0.0, cache.lunghezzaCm("parcella-inesistente"));
    }

    @Test
    void ignoraParcelleSenzaValoreDiLunghezzaRegistrato() {
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(parcella(1L, "parcellaC", null)));

        CacheGermogli cache = new CacheGermogli(repository);
        cache.carica();

        assertEquals(0.0, cache.lunghezzaCm("parcellaC"));
    }
}