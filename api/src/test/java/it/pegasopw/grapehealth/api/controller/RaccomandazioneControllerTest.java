package it.pegasopw.grapehealth.api.controller;

import it.pegasopw.grapehealth.api.exception.ParametriNonValidiException;
import it.pegasopw.grapehealth.api.service.RaccomandazioniService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RaccomandazioneControllerTest {

    private final RaccomandazioniService raccomandazioniService = mock(RaccomandazioniService.class);
    private final RaccomandazioneController controller = new RaccomandazioneController(raccomandazioniService);

    @Test
    void accettaEsattamente500Id() {
        List<Long> id500 = LongStream.rangeClosed(1, 500).boxed().collect(Collectors.toList());
        when(raccomandazioniService.perAllerteMultiple(id500)).thenReturn(List.of());

        assertDoesNotThrow(() -> controller.cerca(null, id500));
        verify(raccomandazioniService).perAllerteMultiple(id500);
    }

    @Test
    void oltre500IdLanciaParametriNonValidiSenzaInterpellareIlService() {
        List<Long> id501 = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toList());

        ParametriNonValidiException ex = assertThrows(ParametriNonValidiException.class,
                () -> controller.cerca(null, id501));
        assertTrue(ex.getMessage().contains("500"));
        verifyNoInteractions(raccomandazioniService);
    }
}