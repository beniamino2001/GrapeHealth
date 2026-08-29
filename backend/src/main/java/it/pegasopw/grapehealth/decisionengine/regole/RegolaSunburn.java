package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Scottatura (sunburn) da esposizione della bacca. Soglia di ingresso nel
 * range di rischio ("moderato") e le quattro coppie soglia/durata di dose
 * letale ("severo") lette a runtime da regola_soglia: la prima da Gambetta
 * et al. (2021), le quattro coppie da Müller et al. (2023) — più lunga
 * l'esposizione, più bassa la soglia di dose letale. Isteresi di 1°C in
 * uscita dal range di rischio — non presente come colonna in regola_soglia,
 * resta costante Java.
 */
@Component
public class RegolaSunburn implements RegolaRischio {

    private static final String TIPO = "sunburn";
    private static final String PARAMETRO = "temperatura_bacca";
    private static final double ISTERESI_USCITA = 1.0;

    private record SogliaLetale(double temperatura, Duration durataMinima) {}

    private final double sogliaModerato;
    private final SogliaLetale[] soglieLetali;

    public RegolaSunburn(CacheSoglieRegole cacheSoglieRegole) {
        this.sogliaModerato = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "moderato").getValoreSoglia();
        this.soglieLetali = cacheSoglieRegole.soglieMultiple(TIPO, PARAMETRO, "severo").stream()
                .map(s -> new SogliaLetale(s.getValoreSoglia(), Duration.ofMinutes(s.getDurataMinimaMinuti())))
                .toArray(SogliaLetale[]::new);
    }

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

        if (valore < sogliaModerato - ISTERESI_USCITA) {
            stato.terminaEpisodio(chiaveEpisodio);
            nuovoLivello = null;
        } else if (valore < sogliaModerato && livelloPrecedente == null) {
            nuovoLivello = null;
        } else {
            stato.iniziaEpisodio(chiaveEpisodio, ora);
            Duration durata = Duration.between(stato.inizioEpisodio(chiaveEpisodio), ora);

            boolean severo = false;
            for (SogliaLetale soglia : soglieLetali) {
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