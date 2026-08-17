package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.AzioneMitigazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaAzioneEntity;
import it.pegasopw.grapehealth.api.repository.AzioneMitigazioneRepository;
import it.pegasopw.grapehealth.api.repository.RegolaAzioneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheAzioniMitigazioneTest {

    private AzioneMitigazioneEntity azione(String codice, String fonte) {
        AzioneMitigazioneEntity entity = new AzioneMitigazioneEntity();
        ReflectionTestUtils.setField(entity, "codice", codice);
        ReflectionTestUtils.setField(entity, "fonteBibliografica", fonte);
        return entity;
    }

    private RegolaAzioneEntity regolaAzione(String regolaCodice, String azioneCodice) {
        RegolaAzioneEntity entity = new RegolaAzioneEntity();
        ReflectionTestUtils.setField(entity, "regolaCodice", regolaCodice);
        ReflectionTestUtils.setField(entity, "azioneCodice", azioneCodice);
        return entity;
    }

    @Test
    void trovaAzionePerCodiceRestituisceLAzioneCaricata() {
        AzioneMitigazioneRepository azioneRepository = mock(AzioneMitigazioneRepository.class);
        RegolaAzioneRepository regolaAzioneRepository = mock(RegolaAzioneRepository.class);
        when(azioneRepository.findAll()).thenReturn(List.of(azione("caolino", "Agriculture 12(4)491")));
        when(regolaAzioneRepository.findAll()).thenReturn(List.of());
        when(regolaAzioneRepository.count()).thenReturn(0L);

        CacheAzioniMitigazione cache = new CacheAzioniMitigazione(azioneRepository, regolaAzioneRepository);
        cache.carica();

        assertEquals("Agriculture 12(4)491", cache.trovaAzionePerCodice("caolino").getFonteBibliografica());
    }

    @Test
    void azioniPerRegolaRestituisceTutteLeAlternativeAssociate() {
        AzioneMitigazioneRepository azioneRepository = mock(AzioneMitigazioneRepository.class);
        RegolaAzioneRepository regolaAzioneRepository = mock(RegolaAzioneRepository.class);
        when(azioneRepository.findAll()).thenReturn(List.of());
        when(regolaAzioneRepository.findAll()).thenReturn(List.of(
                regolaAzione("sunburn", "nebulizzazione"),
                regolaAzione("sunburn", "caolino"),
                regolaAzione("sunburn", "rete_ombreggiante"),
                regolaAzione("sunburn", "zeolite"),
                regolaAzione("stress_idrico", "irrigazione_soccorso")));
        when(regolaAzioneRepository.count()).thenReturn(5L);

        CacheAzioniMitigazione cache = new CacheAzioniMitigazione(azioneRepository, regolaAzioneRepository);
        cache.carica();

        assertEquals(4, cache.azioniPerRegola("sunburn").size());
        assertEquals(1, cache.azioniPerRegola("stress_idrico").size());
        assertTrue(cache.azioniPerRegola("tre_dieci").isEmpty());
    }
}