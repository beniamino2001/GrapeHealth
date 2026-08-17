package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheAzioniMitigazione;
import it.pegasopw.grapehealth.api.cache.CacheRegole;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.model.dto.RaccomandazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaEntity;
import it.pegasopw.grapehealth.api.raccomandazione.MappatoreRaccomandazione;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import it.pegasopw.grapehealth.api.repository.TrattamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class RaccomandazioniServiceTest {

    private AllertaRepository allertaRepository;
    private TrattamentoRepository trattamentoRepository;
    private CacheRegole cacheRegole;
    private CacheAzioniMitigazione cacheAzioniMitigazione;
    private RaccomandazioniService service;

    private AllertaEntity allerta(Long id, String tipo, String livello, String regolaCodice) {
        AllertaEntity entity = new AllertaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "tipo", tipo);
        ReflectionTestUtils.setField(entity, "livelloRischio", livello);
        ReflectionTestUtils.setField(entity, "regolaCodice", regolaCodice);
        return entity;
    }

    @BeforeEach
    void setUp() {
        allertaRepository = mock(AllertaRepository.class);
        trattamentoRepository = mock(TrattamentoRepository.class);
        cacheRegole = mock(CacheRegole.class);
        cacheAzioniMitigazione = mock(CacheAzioniMitigazione.class);
        
        service = new RaccomandazioniService(allertaRepository, trattamentoRepository,
                new MappatoreRaccomandazione(), cacheRegole, cacheAzioniMitigazione);

        when(cacheAzioniMitigazione.azioniPerRegola(anyString())).thenReturn(List.of());
        when(cacheRegole.sogliePerRegola(anyString())).thenReturn(List.of());
    }

    @Test
    void perAllertaLanciaEccezioneSeNonTrovata() {
        when(allertaRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RisorsaNonTrovataException.class, () -> service.perAllerta(999L));
    }

    @Test
    void perAllerteMultipleIgnoraSilenziosamenteGliIdNonTrovati() {
        AllertaEntity sunburn = allerta(38L, "sunburn", "severo", "sunburn");
        when(allertaRepository.findAllById(List.of(999999L, 38L))).thenReturn(List.of(sunburn));
        when(trattamentoRepository.findByAllertaIdIn(List.of(38L))).thenReturn(List.of());
        when(cacheRegole.trovaPerCodice("sunburn")).thenReturn(null);

        List<RaccomandazioneDTO> risultato = service.perAllerteMultiple(List.of(999999L, 38L));

        assertEquals(1, risultato.size());
        assertEquals(38L, risultato.get(0).allertaId());
    }

    @Test
    void perAllertaArricchisceConDescrizioneEFonteDellaRegola() {
        AllertaEntity idrica = allerta(7L, "stress_idrico", "moderato", "stress_idrico");
        when(allertaRepository.findById(7L)).thenReturn(Optional.of(idrica));
        when(trattamentoRepository.findFirstByAllertaId(7L)).thenReturn(Optional.empty());

        RegolaEntity regola = new RegolaEntity();
        ReflectionTestUtils.setField(regola, "descrizione", "Descrizione di prova");
        ReflectionTestUtils.setField(regola, "fonteBibliografica", "Acevedo-Opazo et al. 2010");
        when(cacheRegole.trovaPerCodice("stress_idrico")).thenReturn(regola);

        RaccomandazioneDTO dto = service.perAllerta(7L);

        assertEquals("Descrizione di prova", dto.descrizioneRegola());
        assertEquals("Acevedo-Opazo et al. 2010", dto.fonteBibliograficaRegola());
        assertFalse(dto.basedOnSimulatedExecution());
    }

    @Test
    void perAllerteAttiveRestituisceLeAllerteFiltrateDalRepository() {
        AllertaEntity attiva = allerta(10L, "ondata_di_calore", "moderato", "ondata_di_calore");
        when(allertaRepository.findAll(any(Specification.class))).thenReturn(List.of(attiva));
        when(trattamentoRepository.findByAllertaIdIn(List.of(10L))).thenReturn(List.of());
        when(cacheRegole.trovaPerCodice("ondata_di_calore")).thenReturn(null);

        List<RaccomandazioneDTO> risultato = service.perAllerteAttive();

        assertEquals(1, risultato.size());
        assertEquals(10L, risultato.get(0).allertaId());
    }
}