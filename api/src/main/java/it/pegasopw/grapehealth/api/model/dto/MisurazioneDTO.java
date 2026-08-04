package it.pegasopw.grapehealth.api.model.dto;

import java.time.Instant;

public record MisurazioneDTO(
        Long id, String nodoCodice, String parcella, String parametro,
        double valore, String unitaMisura, Instant rilevatoIl, Instant ricevutoIl) {
}