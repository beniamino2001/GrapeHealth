package it.pegasopw.grapehealth.decisionengine.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// @NotBlank/@NotNull qui non scattano da soli: MisurazioneListener non passa
// da un controller Spring MVC (che applicherebbe @Valid automaticamente), ma
// deserializza esplicitamente col JsonMapper. La validazione va quindi
// invocata a mano subito dopo il parsing, in MisurazioneListener - stesso
// principio fail-fast già usato da CacheSoglieRegole.sogliaUnica().
public record MisurazioneMessage(
        @NotBlank String nodo,
        @NotBlank String parcella,
        @NotBlank String parametro,
        double valore,
        @JsonProperty("unita_misura") @NotBlank String unitaMisura,
        @JsonProperty("timestamp_rilevazione") @NotNull Instant timestampRilevazione
) {}