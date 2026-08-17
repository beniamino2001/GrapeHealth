package it.pegasopw.grapehealth.api.model.dto;

import java.time.Instant;
import java.util.List;

public record RaccomandazioneDTO(
        Long allertaId,
        String tipoAllerta,
        String livelloRischio,
        String azioneConsigliata,
        String testoRaccomandazione,
        boolean basedOnSimulatedExecution,
        String azioneEseguita,
        String esitoSimulato,
        Instant eseguitaIl,
        String descrizioneRegola,
        String fonteBibliograficaRegola,
        List<AzioneMitigazioneDTO> azioniAlternative,
        List<SogliaDTO> soglieRegola
) {
}