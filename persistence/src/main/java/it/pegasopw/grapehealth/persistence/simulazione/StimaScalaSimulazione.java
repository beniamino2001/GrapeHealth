package it.pegasopw.grapehealth.persistence.simulazione;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// Stima il fattore di accelerazione (--time-scale) del simulatore Python
// deducendolo a runtime confrontando tra due misurazioni consecutive che
// arrivano dalla coda, quanto e' avanzato l'orologio SIMULATO
// (timestamp_rilevazione) rispetto a quanto e' effettivamente trascorso
// sull'orologio REALE (Instant.now()) tra l'arrivo dell'una e dell'altra.
@Component
public class StimaScalaSimulazione {

    private static final Logger log = LoggerFactory.getLogger(StimaScalaSimulazione.class);

    // Media mobile esponenziale: smorza il rumore dovuto a jitter di rete/
    // scheduling tra un messaggio e l'altro, evitando che una singola coppia
    // di misurazioni ravvicinate sballi la stima da una lettura all'altra.
    private static final double ALPHA_SMOOTHING = 0.2;
    private static final double SCALA_ISTANTANEA_MASSIMA = 100_000.0;

    private volatile Instant ultimoTimestampSimulato;
    private volatile Instant ultimoArrivoReale;
    private volatile double scalaStimata = 1.0; // di default: nessuna accelerazione finche' non si osserva altro

    public void osserva(Instant timestampSimulato) {
        osserva(timestampSimulato, Instant.now());
    }

    // Package-private: consente ai test di controllare l'istante reale
    // osservato, invece di dipendere da Instant.now() e dal tempo di
    // esecuzione reale del test stesso.
    synchronized void osserva(Instant timestampSimulato, Instant istanteReale) {
        if (ultimoTimestampSimulato != null && ultimoArrivoReale != null) {
            Duration deltaSimulato = Duration.between(ultimoTimestampSimulato, timestampSimulato);
            Duration deltaReale = Duration.between(ultimoArrivoReale, istanteReale);

            if (!deltaReale.isNegative() && !deltaReale.isZero() && !deltaSimulato.isNegative()) {
                double scalaIstantanea = deltaSimulato.toNanos() / (double) deltaReale.toNanos();
                if (scalaIstantanea > 0) {
                    scalaIstantanea = Math.min(scalaIstantanea, SCALA_ISTANTANEA_MASSIMA);
                    scalaStimata = ALPHA_SMOOTHING * scalaIstantanea + (1 - ALPHA_SMOOTHING) * scalaStimata;
                    log.debug("Scala simulazione aggiornata: istantanea={}, media mobile={}", scalaIstantanea, scalaStimata);
                }
            }
        }
        ultimoTimestampSimulato = timestampSimulato;
        ultimoArrivoReale = istanteReale;
    }

    public double scalaCorrente() {
        return scalaStimata;
    }
}