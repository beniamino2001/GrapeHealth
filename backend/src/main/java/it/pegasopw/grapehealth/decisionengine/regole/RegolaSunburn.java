package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class RegolaSunburn implements RegolaRischio {

    private static final String TIPO = "sunburn";
    private static final String PARAMETRO = "temperatura_bacca";

    // Soglia di ingresso nel range di rischio definito in "Gambetta et al. 2021", ovvero 45-49°C
    private static final double SOGLIA_MODERATO = 45.0;
    private static final double ISTERESI_USCITA = 1.0;

    private record SogliaLetale(double temperatura, Duration durataMinima) {}

    // Soglie letali dipendenti dalla durata di esposizione (Schmidt et al. 2023): più lunga l'esposizione, più bassa la soglia di dose letale.
    private static final SogliaLetale[] SOGLIE_LETALI = {
            new SogliaLetale(53.79, Duration.ofMinutes(15)),
            new SogliaLetale(49.94, Duration.ofMinutes(30)),
            new SogliaLetale(47.82, Duration.ofMinutes(60)),
            new SogliaLetale(47.06, Duration.ofMinutes(90)),
    };

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return PARAMETRO.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        String chiave = TIPO + ":" + m.nodo();
        String chiaveEpisodio = chiave + ":episodio";
        double valore = m.valore();
        Instant ora = m.timestampRilevazione();
        String livelloPrecedente = stato.livelloRischio(chiave);
        String nuovoLivello;

        if (valore < SOGLIA_MODERATO - ISTERESI_USCITA) {
            stato.terminaEpisodio(chiaveEpisodio);
            nuovoLivello = null;
        } else if (valore < SOGLIA_MODERATO && livelloPrecedente == null) {
            nuovoLivello = null;
        } else {
            stato.iniziaEpisodio(chiaveEpisodio, ora);
            Duration durata = Duration.between(stato.inizioEpisodio(chiaveEpisodio), ora);

            boolean severo = false;
            for (SogliaLetale soglia : SOGLIE_LETALI) {
                if (valore >= soglia.temperatura() && !durata.minus(soglia.durataMinima()).isNegative()) {
                    severo = true;
                    break;
                }
            }
            nuovoLivello = severo ? "severo" : "moderato";

            // Il danno da scottatura è irreversibile all'interno dello stesso periodo temporale:
            // una volta raggiunta la dose letale (severo), una lettura che scende
            // temporaneamente sotto la soglia specifica non riporta la bacca a un
            // livello di rischio inferiore. Evita oscillazioni severo/moderato dovute
            // a fluttuazioni della temperatura attorno a una soglia.
            if ("severo".equals(livelloPrecedente)) {
                nuovoLivello = "severo";
            }
        }

        if (Objects.equals(nuovoLivello, livelloPrecedente)) {
            return Optional.empty();
        }
        stato.aggiornaLivelloRischio(chiave, nuovoLivello);
        if (nuovoLivello == null) {
            return Optional.empty();
        }

        return Optional.of(new AllertaEvent(
                TIPO, nuovoLivello, m.nodo(), m.parcella(), m.parametro(), valore, messaggio(valore, nuovoLivello), ora));
    }

    private String messaggio(double valore, String livello) {
        if (livello.equals("severo")) {
            return "Temperatura della superficie della bacca a %.1f°C: dose letale raggiunta per la durata di esposizione osservata"
                    .formatted(valore);
        }
        return "Temperatura della superficie della bacca a %.1f°C, nel range di rischio sunburn (45-49°C)"
                .formatted(valore);
    }
}