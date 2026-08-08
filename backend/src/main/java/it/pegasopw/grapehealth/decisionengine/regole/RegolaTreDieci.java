package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
public class RegolaTreDieci implements RegolaRischio {

    private static final String TIPO = "tre_dieci";
    private static final String PARAMETRO_TEMPERATURA = "temperatura_aria";
    private static final String PARAMETRO_PIOGGIA = "pioggia";

    private static final double SOGLIA_TEMPERATURA = 10.0;
    private static final double SOGLIA_PIOGGIA_MM = 10.0;
    private static final double SOGLIA_GERMOGLI_CM = 10.0;

    private static final Duration FINESTRA_PIOGGIA = Duration.ofHours(48);

    private final CacheGermogli cacheGermogli;

    public RegolaTreDieci(CacheGermogli cacheGermogli) {
        this.cacheGermogli = cacheGermogli;
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return PARAMETRO_TEMPERATURA.equals(m.parametro()) || PARAMETRO_PIOGGIA.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        // A differenza delle altre regole valutate per singolo nodo, qui lo stato è
        // tenuto per PARCELLA: la regola dei tre dieci combina concettualmente due
        // grandezze (temperatura e pioggia) che, anche se nel simulatore attuale
        // condividono lo stesso nodo fisico "meteo", rappresentano una valutazione
        // di rischio a livello dell'intera parcella, non del singolo sensore.
        String chiaveParcella = TIPO + ":" + m.parcella();
        String chiaveTemperatura = chiaveParcella + ":temperatura";
        String chiavePioggia = chiaveParcella + ":pioggia";

        if (PARAMETRO_TEMPERATURA.equals(m.parametro())) {
            stato.registraValoreCorrente(chiaveTemperatura, m.valore());
        } else {
            stato.registraLetturaTemporale(chiavePioggia, m.timestampRilevazione(), m.valore());
        }

        Double temperaturaCorrente = stato.valoreCorrente(chiaveTemperatura);
        double pioggiaCumulata = stato.sommaFinestra(chiavePioggia, m.timestampRilevazione(), FINESTRA_PIOGGIA);
        double germogliCm = cacheGermogli.lunghezzaCm(m.parcella());

        boolean condizioneVerificata = temperaturaCorrente != null
                && temperaturaCorrente >= SOGLIA_TEMPERATURA
                && pioggiaCumulata >= SOGLIA_PIOGGIA_MM
                && germogliCm >= SOGLIA_GERMOGLI_CM;

        String livelloPrecedente = stato.livelloRischio(chiaveParcella);
        String nuovoLivello = condizioneVerificata ? "moderato" : null;

        if (Objects.equals(nuovoLivello, livelloPrecedente)) {
            return Optional.empty();
        }

        stato.aggiornaLivelloRischio(chiaveParcella, nuovoLivello);

        if (nuovoLivello == null) {
            return Optional.empty();
        }

        String messaggio = ("Condizioni dei \"tre dieci\" verificate su %s: temperatura %.1f°C, " +
                "pioggia cumulata %.1f mm nelle ultime %d ore, germogli a %.0f cm — " +
                "rischio di infezione primaria da peronospora")
                .formatted(m.parcella(), temperaturaCorrente, pioggiaCumulata, FINESTRA_PIOGGIA.toHours(), germogliCm);

        return Optional.of(new AllertaEvent(
                TIPO, "moderato", m.nodo(), m.parcella(), m.parametro(), m.valore(), messaggio, m.timestampRilevazione()));
    }
}