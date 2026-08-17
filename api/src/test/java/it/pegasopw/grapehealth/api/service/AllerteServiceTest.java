package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AllerteServiceTest {

    private final AllertaRepository allertaRepository = mock(AllertaRepository.class);
    private final CacheNodi cacheNodi = mock(CacheNodi.class);
    private final CacheParcelle cacheParcelle = mock(CacheParcelle.class);
    private final AllerteService service = new AllerteService(allertaRepository, cacheNodi, cacheParcelle);

    private final Pageable pageable = PageRequest.of(0, 50);

    @Test
    void parcellaSconosciutaRestituiscePaginaVuotaSenzaInterrogareIlRepository() {
        when(cacheParcelle.idPerNome("parcellaZ")).thenReturn(null);

        Page<AllertaDTO> risultato = service.cerca(null, null, "parcellaZ", pageable);

        assertTrue(risultato.isEmpty());
        verifyNoInteractions(allertaRepository);
    }

    @Test
    void statoOmessoInterrogaComunqueIlRepository() {
        when(allertaRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        service.cerca(null, null, null, pageable);

        verify(allertaRepository).findAll(any(Specification.class), eq(pageable));
        // Se il valore applicato al filtro sia davvero "attiva" non è verificabile
        // con un mock puro, perché è incapsulato dentro la Specification passata
        // al repository: quella parte resta coperta dalla richiesta Postman
        // "/api/allerte" senza parametro stato, gia' presente nella collection in tests/postman.
    }
}