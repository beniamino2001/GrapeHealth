package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.regole.support.RegolaSogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.regole.support.SogliaConIsteresi;
import org.springframework.stereotype.Component;

@Component
public class RegolaOndataDiCalore extends RegolaSogliaConIsteresi {

    public RegolaOndataDiCalore() {
        super("ondata_di_calore", "temperatura_aria",
                SogliaConIsteresi.sogliaSingola(35.0, 1.0, SogliaConIsteresi.Verso.PEGGIORA_SALENDO));
    }

    @Override
    protected String messaggio(double valore, String livello) {
        return "Temperatura dell'aria a %.1f°C, sopra la soglia critica di %.0f°C (attivazione mitigazione)"
                .formatted(valore, soglie().sogliaModerato());
    }
}