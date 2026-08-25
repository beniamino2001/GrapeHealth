package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Danno alla sopravvivenza radicale da temperatura del suolo eccessiva
 * (Field et al., 2020, citando Huang et al., 2005): sopra la soglia letta a
 * runtime da regola_soglia, la temperatura della zona radicale può
 * compromettere la sopravvivenza delle radici. Fenomeno fisiologico,
 * distinto dall'uso fitosanitario dello stesso parametro in
 * RegolaSvernamentoOospore.
 *
 * Un solo livello di rischio, "severo" fin dal primo superamento — a
 * differenza delle altre regole a soglia singola di questo modulo
 * (RegolaOndataDiCalore usa "moderato"), perché la fonte descrive
 * direttamente un danno alla sopravvivenza, non un primo segnale di
 * allarme: non esiste un livello intermedio da rappresentare.
 *
 * Isteresi di 1°C — non presente come colonna in regola_soglia, resta
 * costante Java: la soglia è vicina al tetto realistico di temperatura del
 * suolo in ondata di calore, un margine stretto con un rischio concreto di
 * oscillazione dovuta al rumore di misura, sullo stesso principio già
 * osservato per psi_stem e temperatura_aria.
 */
@Component
public class RegolaDannoRadicale implements RegolaRischio {

    private static final String TIPO = "danno_radicale";
    private static final String PARAMETRO = "temperatura_suolo";
    private static final double ISTERESI = 1.0;

    private final double soglia;

    public RegolaDannoRadicale(CacheSoglieRegole cacheSoglieRegole) {
        this.soglia = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "severo").getValoreSoglia();
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
        String livelloPrecedente = stato.livelloRischio(chiave);
        boolean eraAttivo = livelloPrecedente != null;

        boolean condizioneVerificata = m.valore() >= soglia
                || (eraAttivo && m.valore() >= soglia - ISTERESI);

        if (condizioneVerificata && !eraAttivo) {
            stato.aggiornaLivelloRischio(chiave, "severo");
            return Optional.of(new AllertaEvent(
                    TIPO, "severo", m.nodo(), m.parcella(), m.parametro(), m.valore(),
                    "Temperatura del suolo a %.1f°C, oltre la soglia critica di %.0f°C per la sopravvivenza radicale"
                            .formatted(m.valore(), soglia),
                    m.timestampRilevazione()));
        }

        if (!condizioneVerificata && eraAttivo) {
            stato.aggiornaLivelloRischio(chiave, null);
        }

        return Optional.empty();
    }
}