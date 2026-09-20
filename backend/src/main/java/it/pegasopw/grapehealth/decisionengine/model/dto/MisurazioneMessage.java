package it.pegasopw.grapehealth.decisionengine.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record MisurazioneMessage(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$") String nodo,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$") String parcella,
        @NotBlank String parametro,
        double valore,
        @JsonProperty("unita_misura") @NotBlank String unitaMisura,
        @JsonProperty("timestamp_rilevazione") @NotNull Instant timestampRilevazione
) {}