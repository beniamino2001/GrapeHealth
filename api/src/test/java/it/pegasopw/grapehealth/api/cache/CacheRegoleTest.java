package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.RegolaEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.api.repository.RegolaRepository;
import it.pegasopw.grapehealth.api.repository.RegolaSogliaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheRegoleTest {

    private RegolaEntity regola(String codice, String fonte) {
        RegolaEntity entity = new RegolaEntity();
        ReflectionTestUtils.setField(entity, "codice", codice);
        ReflectionTestUtils.setField(entity, "fonteBibliografica", fonte);
        return entity;
    }

    private RegolaSogliaEntity soglia(String regolaCodice, String livello) {
        RegolaSogliaEntity entity = new RegolaSogliaEntity();
        ReflectionTestUtils.setField(entity, "regolaCodice", regolaCodice);
        ReflectionTestUtils.setField(entity, "livelloRischio", livello);
        return entity;
    }

    @Test
    void trovaPerCodiceRestituisceLaRegolaCaricata() {
        RegolaRepository regolaRepository = mock(RegolaRepository.class);
        RegolaSogliaRepository sogliaRepository = mock(RegolaSogliaRepository.class);
        when(regolaRepository.findAll()).thenReturn(List.of(regola("sunburn", "Müller et al. 2023")));
        when(sogliaRepository.findAll()).thenReturn(List.of());
        when(sogliaRepository.count()).thenReturn(0L);

        CacheRegole cache = new CacheRegole(regolaRepository, sogliaRepository);
        cache.carica();

        assertEquals("Müller et al. 2023", cache.trovaPerCodice("sunburn").getFonteBibliografica());
    }

    @Test
    void sogliePerRegolaRaggruppaCorrettamentePerRegolaCodice() {
        RegolaRepository regolaRepository = mock(RegolaRepository.class);
        RegolaSogliaRepository sogliaRepository = mock(RegolaSogliaRepository.class);
        when(regolaRepository.findAll()).thenReturn(List.of());
        when(sogliaRepository.findAll()).thenReturn(List.of(
                soglia("sunburn", "moderato"),
                soglia("sunburn", "severo"),
                soglia("stress_idrico", "moderato")));
        when(sogliaRepository.count()).thenReturn(3L);

        CacheRegole cache = new CacheRegole(regolaRepository, sogliaRepository);
        cache.carica();

        assertEquals(2, cache.sogliePerRegola("sunburn").size());
        assertEquals(1, cache.sogliePerRegola("stress_idrico").size());
        assertTrue(cache.sogliePerRegola("tipo_sconosciuto").isEmpty());
    }
}