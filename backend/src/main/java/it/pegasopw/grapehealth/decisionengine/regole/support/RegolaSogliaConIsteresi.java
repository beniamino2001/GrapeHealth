package it.pegasopw.grapehealth.decisionengine.regole.support;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.regole.RegolaRischio;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;

import java.util.Objects;
import java.util.Optional;

/**
 * Fattorizza la logica comune alle regole a soglia fissa con isteresi
 * (stress idrico, ondata di calore): calcolo della chiave di stato per nodo,
 * confronto col livello precedente, deduplica delle ripubblicazioni,
 * costruzione dell'evento. Le sottoclassi forniscono solo il tipo di regola,
 * il parametro osservato, le soglie numeriche (tramite SogliaConIsteresi) e
 * il testo del messaggio — non reimplementano la logica di transizione.
 */
public abstract class RegolaSogliaConIsteresi implements RegolaRischio {

    private final String tipo;
    private final String parametro;
    private final SogliaConIsteresi soglie;

    protected RegolaSogliaConIsteresi(String tipo, String parametro, SogliaConIsteresi soglie) {
        this.tipo = tipo;
        this.parametro = parametro;
        this.soglie = soglie;
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return parametro.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        double valore = m.valore();
        String chiave = tipo + ":" + m.nodo();
        String livelloPrecedente = stato.livelloRischio(chiave);

        String nuovoLivello = soglie.calcolaLivello(valore, livelloPrecedente);

        if (Objects.equals(nuovoLivello, livelloPrecedente)) {
            return Optional.empty();
        }

        stato.aggiornaLivelloRischio(chiave, nuovoLivello);

        if (nuovoLivello == null) {
            return Optional.empty();
        }

        return Optional.of(new AllertaEvent(
                tipo, nuovoLivello, m.nodo(), m.parcella(), m.parametro(), valore,
                messaggio(m, stato, nuovoLivello), m.timestampRilevazione()));
    }

    /**
     * Costruisce il testo dell'allerta. Riceve la misurazione e lo stato
     * completi, non solo valore e livello, perché alcune sottoclassi (es.
     * RegolaStressIdrico) arricchiscono il messaggio con un indicatore
     * complementare tracciato separatamente in StatoRischio.
     */
    protected abstract String messaggio(MisurazioneMessage misurazione, StatoRischio stato, String livello);

    protected SogliaConIsteresi soglie() {
        return soglie;
    }
}