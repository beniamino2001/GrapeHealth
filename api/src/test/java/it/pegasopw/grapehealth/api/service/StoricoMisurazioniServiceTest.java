package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.ParametriNonValidiException;
import it.pegasopw.grapehealth.api.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.api.service.support.NodoResolver;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoricoMisurazioniServiceTest {

    private final MisurazioneRepository misurazioneRepository = mock(MisurazioneRepository.class);
    private final NodoResolver nodoResolver = mock(NodoResolver.class);
    private final CacheNodi cacheNodi = mock(CacheNodi.class);
    private final CacheParcelle cacheParcelle = mock(CacheParcelle.class);
    private final StoricoMisurazioniService service = new StoricoMisurazioniService(misurazioneRepository, nodoResolver,
            cacheNodi, cacheParcelle);

    private final Pageable pageable = PageRequest.of(0, 50);

    @Test
    void dalSuccessivoAdAlLanciaParametriNonValidi() {
        Instant dal = Instant.parse("2026-08-03T00:00:00Z");
        Instant al = Instant.parse("2026-08-01T00:00:00Z");

        ParametriNonValidiException ex = assertThrows(ParametriNonValidiException.class,
                () -> service.cerca(null, null, dal, al, pageable));
        assertTrue(ex.getMessage().contains("dal"));
        verifyNoInteractions(misurazioneRepository);
    }

    @Test
    void parcellaSenzaNodiRestituiscePaginaVuotaSenzaInterrogareIlRepository() {
        when(nodoResolver.nodoIdsPerParcella("parcellaSenzaNodi")).thenReturn(List.of());

        var risultato = service.cerca("parcellaSenzaNodi", null, null, null, pageable);

        assertTrue(risultato.isEmpty());
        verifyNoInteractions(misurazioneRepository);
    }

    @Test
    void parcellaNonFiltrataInterrogaIlRepository() {
        when(nodoResolver.nodoIdsPerParcella(null)).thenReturn(null);
        when(misurazioneRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        service.cerca(null, "temperatura_aria", null, null, pageable);

        verify(misurazioneRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void finestraDalAlNonPassaDaPageableERestituisceOgniRigaCandidata() {
        Instant dal = Instant.parse("2026-08-01T00:00:00Z");
        Instant al = Instant.parse("2026-08-03T00:00:00Z");
        List<MisurazioneEntity> candidati = List.of(
                mock(MisurazioneEntity.class), mock(MisurazioneEntity.class), mock(MisurazioneEntity.class));
        when(nodoResolver.nodoIdsPerParcella(null)).thenReturn(null);
        when(misurazioneRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(candidati);

        Page<MisurazioneDTO> risultato = service.cerca(null, null, dal, al, pageable);

        assertEquals(3, risultato.getTotalElements());
        assertEquals(3, risultato.getContent().size());
        verify(misurazioneRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void finestraTemporaleOltreIlLimiteLanciaEccezione() {
        when(nodoResolver.nodoIdsPerParcella(null)).thenReturn(null); // <- riga aggiunta
        when(misurazioneRepository.count(any(Specification.class))).thenReturn(20_001L);
        assertThrows(ParametriNonValidiException.class,
                () -> service.cerca(null, null, Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"), Pageable.unpaged()));
    }
}