package it.pegasopw.grapehealth.attuatori.model.evento;

import java.time.Instant;

public record AllertaEvent(
        String tipo,            // es. "stress_idrico"
        String livelloRischio,  // "moderato" | "severo"
        String nodo,
        String parcella,
        String parametro,
        double valoreOsservato,
        String messaggio,
        Instant timestamp
) {}