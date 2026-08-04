package it.pegasopw.grapehealth.api.model.dto;

import java.time.Instant;

public record AllertaDTO(
        Long id, String tipo, String livelloRischio, String nodoCodice, String parcella,
        String descrizione, String regolaScatenante, Instant generataIl, Instant risoltaIl, String stato) {
}