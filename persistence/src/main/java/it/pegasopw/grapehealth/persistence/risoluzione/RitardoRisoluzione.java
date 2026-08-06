package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.simulazione.StimaScalaSimulazione;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Ritardi PRIMA della scalatura: rappresentano la durata realistica con cui,
// nella realta' agricola, ci si aspetterebbe di vedere risolto il rischio
// segnalato da un'allerta dopo l'esecuzione del trattamento corrispondente.
//
// Quando il simulatore gira accelerato la finestra reale di attesa 
// si accorcia proporzionalmente, fino al pavimento RITARDO_MINIMO sotto 
// il quale la dashboard perderebbe la possibilita' di mostrare l'allerta 
// come attiva anche solo per un istante percepibile.
@Component
public class RitardoRisoluzione {

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";
    private static final String SEVERO = "severo";

    private static final Duration RITARDO_MINIMO = Duration.ofSeconds(10);

    private final StimaScalaSimulazione stimaScalaSimulazione;

    public RitardoRisoluzione(StimaScalaSimulazione stimaScalaSimulazione) {
        this.stimaScalaSimulazione = stimaScalaSimulazione;
    }

    public Duration perAllerta(String tipo, String livelloRischio) {
        Duration base = ritardoBase(tipo, livelloRischio);
        double scala = stimaScalaSimulazione.scalaCorrente();

        long nanosScalati = Math.round(base.toNanos() / scala);
        Duration scalato = Duration.ofNanos(nanosScalati);

        return scalato.compareTo(RITARDO_MINIMO) < 0 ? RITARDO_MINIMO : scalato;
    }

    // Durate realistiche a scala 1 (nessuna accelerazione):
    // - stress_idrico: l'irrigazione di soccorso richiede ore per iniziare a
    //   normalizzare il Psi_stem, con un deficit severo che necessita di
    //   piu' tempo del moderato.
    // - sunburn/ondata_di_calore: la nebulizzazione raffredda la superficie
    //   della bacca/l'aria nel giro di decine di minuti.
    // - tre_dieci: periodo di verifica/efficacia tipico di un trattamento
    //   fitosanitario, dell'ordine di una giornata.
    private Duration ritardoBase(String tipo, String livelloRischio) {
        boolean severo = SEVERO.equals(livelloRischio);
        return switch (tipo) {
            case STRESS_IDRICO -> severo ? Duration.ofHours(4) : Duration.ofHours(2);
            case SUNBURN -> severo ? Duration.ofMinutes(40) : Duration.ofMinutes(20);
            case ONDATA_DI_CALORE -> Duration.ofMinutes(20);
            case TRE_DIECI -> Duration.ofHours(24);
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da RitardoRisoluzione: " + tipo);
        };
    }
}