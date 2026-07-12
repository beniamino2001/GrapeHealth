package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.regole.support.RegolaSogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.regole.support.SogliaConIsteresi;
import org.springframework.stereotype.Component;

@Component
public class RegolaStressIdrico extends RegolaSogliaConIsteresi {

    public RegolaStressIdrico() {
        super("stress_idrico", "psi_stem",
                SogliaConIsteresi.dueSoglie(-1.2, -1.4, 0.05, SogliaConIsteresi.Verso.PEGGIORA_SCENDENDO));
    }

    @Override
    protected String messaggio(double valore, String livello) {
        double soglia = livello.equals("severo") ? soglie().sogliaSevero() : soglie().sogliaModerato();
        return "Potenziale idrico dello stelo a %.2f MPa, sotto la soglia critica di %.2f MPa"
                .formatted(valore, soglia);
    }
}