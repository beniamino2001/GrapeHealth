package it.pegasopw.grapehealth.decisionengine.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

// @NotBlank/@NotNull/@Pattern qui non scattano da soli: MisurazioneListener non passa
// da un controller Spring MVC (che applicherebbe @Valid automaticamente), ma
// deserializza esplicitamente col JsonMapper. La validazione va quindi
// invocata a mano subito dopo il parsing, in MisurazioneListener - stesso
// principio fail-fast già usato da CacheSoglieRegole.sogliaUnica().
// @Pattern su nodo/parcella: questi due valori finiscono sia nei messaggi di
// log sia nella routing key AMQP pubblicata da AllertaPublisher
// ("allerta.<tipo>.<parcella>.<nodo>") - un carattere come "." o "#" qui
// altererebbe la struttura della routing key stessa, non solo il suo contenuto.
public record MisurazioneMessage(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$") String nodo,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$") String parcella,
        @NotBlank String parametro,
        double valore,
        @JsonProperty("unita_misura") @NotBlank String unitaMisura,
        @JsonProperty("timestamp_rilevazione") @NotNull Instant timestampRilevazione
) {}