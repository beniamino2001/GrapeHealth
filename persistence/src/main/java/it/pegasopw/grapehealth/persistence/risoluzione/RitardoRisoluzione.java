package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.simulazione.StimaScalaSimulazione;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Ritardi PRIMA della scalatura: rappresentano la durata realistica con cui,
// nella realta' agricola, ci si aspetterebbe di vedere risolto il rischio
// segnalato da un'allerta dopo l'esecuzione del trattamento corrispondente -
// o, per i tipi senza un'azione catalogata (v. MappatoreAzione), quanto a
// lungo ha senso che l'allerta resti visibile come "attiva" prima di
// considerarla superata.
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
    private static final String SVERNAMENTO_OOSPORE = "svernamento_oospore";
    private static final String INFEZIONE_SECONDARIA = "infezione_secondaria";
    private static final String DANNO_RADICALE = "danno_radicale";
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
    //   della bacca/l'aria nel giro di decine di minuti; entrambe hanno oggi
    //   due livelli e lo stesso rapporto severo>moderato dello stress
    //   idrico, con gli stessi identici valori del sunburn dato che
    //   condividono la stessa azione di mitigazione (nebulizzazione).
    // - tre_dieci: qui il rapporto moderato/severo è INVERTITO rispetto agli
    //   altri tipi a due livelli. "Moderato" segnala l'infezione primaria
    //   appena rilevata (trigger di Baldacci): c'è tipicamente più margine
    //   prima di dover intervenire. "Severo" segnala che l'incubazione
    //   secondo Goidanich ha raggiunto il 70%, cioè che la finestra di
    //   trattamento si sta chiudendo — più urgente, non meno, quindi un
    //   ritardo di risoluzione più BREVE. Le due durate restano comunque una
    //   stima di ordine di grandezza, non legata a una fonte bibliografica
    //   specifica per l'una o l'altra, come già il valore singolo precedente.
    // - svernamento_oospore/infezione_secondaria/danno_radicale: nessuna
    //   azione catalogata (v. MappatoreAzione), quindi qui il ritardo non
    //   rappresenta un tempo di risposta a un trattamento ma solo una
    //   finestra di visibilità dell'allerta come "attiva". Allineate
    //   all'ordine di grandezza di tre_dieci moderato (stessa natura di
    //   finestra stagionale di rischio per le prime due; per danno_radicale,
    //   estesa per coerenza in assenza di un'indicazione bibliografica
    //   propria), stessa natura di stima non bibliografica in tutti e tre i
    //   casi.
    private Duration ritardoBase(String tipo, String livelloRischio) {
        boolean severo = SEVERO.equals(livelloRischio);
        return switch (tipo) {
            case STRESS_IDRICO -> severo ? Duration.ofHours(4) : Duration.ofHours(2);
            case SUNBURN, ONDATA_DI_CALORE -> severo ? Duration.ofMinutes(40) : Duration.ofMinutes(20);
            case TRE_DIECI -> severo ? Duration.ofHours(6) : Duration.ofHours(24);
            case SVERNAMENTO_OOSPORE, INFEZIONE_SECONDARIA, DANNO_RADICALE -> Duration.ofHours(24);
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da RitardoRisoluzione: " + tipo);
        };
    }
}