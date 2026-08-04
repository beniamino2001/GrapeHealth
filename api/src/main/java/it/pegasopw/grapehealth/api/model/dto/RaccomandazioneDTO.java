package it.pegasopw.grapehealth.api.model.dto;

import java.time.Instant;

/**
 * Il motore primario e' rule-based (mappatura statica tipo allerta -> azione consigliata,
 * in MappatoreRaccomandazione). Quando esiste un trattamento simulato collegato alla stessa
 * allerta, i campi azioneEseguita/esitoSimulato/eseguitaIl vengono valorizzati e
 * basedOnSimulatedExecution passa a true: la raccomandazione non e' piu' solo teorica ma
 * confermata da un'esecuzione (simulata) effettivamente avvenuta.
 */
public record RaccomandazioneDTO(
        Long allertaId,
        String tipoAllerta,
        String livelloRischio,
        String azioneConsigliata,
        String testoRaccomandazione,
        boolean basedOnSimulatedExecution,
        String azioneEseguita,
        String esitoSimulato,
        Instant eseguitaIl
) {
}