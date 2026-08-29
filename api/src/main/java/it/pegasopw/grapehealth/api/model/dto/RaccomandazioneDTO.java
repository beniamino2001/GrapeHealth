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
        // Le soglie bibliografiche che definiscono la regola (es. per sunburn, le
        // quattro coppie soglia/durata LT50 di Müller et al. 2023): prima
        // consultabili solo nel codice Java del decision engine, mai tramite l'API
        // nonostante fossero gia' a database.
        List<SogliaDTO> soglieRegola
) {
}