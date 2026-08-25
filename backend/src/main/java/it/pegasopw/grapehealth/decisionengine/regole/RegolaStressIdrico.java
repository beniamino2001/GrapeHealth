package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.regole.support.RegolaSogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.regole.support.SogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stress idrico da potenziale idrico dello stelo a mezzogiorno (psi_stem).
 * Soglie lette a runtime da regola_soglia (moderato/severo su psi_stem):
 * verificate contro Acevedo-Opazo et al. (2010) al momento della semina
 * dello schema, non nel codice. Isteresi di 0,05 MPa — non presente come
 * colonna in regola_soglia, resta costante Java. Il verso PEGGIORA_SCENDENDO
 * riflette che psi_stem è negativo e il rischio cresce quando il valore
 * scende (diventa più negativo), non quando sale.
 *
 * L'umidità del suolo (parametro "umidita_suolo", pubblicato dal nodo suolo
 * della stessa parcella) è un indicatore complementare, non sostitutivo, di
 * psi_stem: viene tracciata per parcella e riportata nel messaggio quando
 * psi_stem fa scattare un'allerta, come conferma incrociata — non genera mai
 * un'allerta autonomamente, né influenza la soglia o il livello determinati
 * da psi_stem.
 */
@Component
public class RegolaStressIdrico extends RegolaSogliaConIsteresi {

    private static final String TIPO = "stress_idrico";
    private static final String PARAMETRO = "psi_stem";
    private static final double ISTERESI = 0.05;
    private static final String PARAMETRO_UMIDITA_SUOLO = "umidita_suolo";

    public RegolaStressIdrico(CacheSoglieRegole cacheSoglieRegole) {
        super(TIPO, PARAMETRO, SogliaConIsteresi.dueSoglie(
                cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "moderato").getValoreSoglia(),
                cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "severo").getValoreSoglia(),
                ISTERESI, SogliaConIsteresi.Verso.PEGGIORA_SCENDENDO));
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return super.isApplicabile(m) || PARAMETRO_UMIDITA_SUOLO.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (PARAMETRO_UMIDITA_SUOLO.equals(m.parametro())) {
            stato.registraValoreCorrente(chiaveUmiditaSuolo(m.parcella()), m.valore());
            return Optional.empty();
        }
        return super.valuta(m, stato);
    }

    @Override
    protected String messaggio(MisurazioneMessage m, StatoRischio stato, String livello) {
        double soglia = livello.equals("severo") ? soglie().sogliaSevero() : soglie().sogliaModerato();
        String base = "Potenziale idrico dello stelo a %.2f MPa, sotto la soglia critica di %.2f MPa"
                .formatted(m.valore(), soglia);

        Double umiditaSuolo = stato.valoreCorrente(chiaveUmiditaSuolo(m.parcella()));
        if (umiditaSuolo == null) {
            return base;
        }
        return base + ", umidità del suolo al %.1f%% (indicatore complementare)".formatted(umiditaSuolo);
    }

    private String chiaveUmiditaSuolo(String parcella) {
        return "stress_idrico:umidita_suolo:" + parcella;
    }
}