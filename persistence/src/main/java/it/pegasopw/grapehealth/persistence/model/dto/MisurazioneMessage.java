package it.pegasopw.grapehealth.persistence.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record MisurazioneMessage(
        String nodo,
        String parcella,
        String parametro,
        double valore,
        @JsonProperty("unita_misura") String unitaMisura,
        @JsonProperty("timestamp_rilevazione") Instant timestampRilevazione
) {}