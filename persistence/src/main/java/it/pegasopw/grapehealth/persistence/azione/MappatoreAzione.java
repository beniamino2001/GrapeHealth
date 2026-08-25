package it.pegasopw.grapehealth.persistence.azione;

import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class MappatoreAzione {

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";
    private static final String SVERNAMENTO_OOSPORE = "svernamento_oospore";
    private static final String INFEZIONE_SECONDARIA = "infezione_secondaria";
    private static final String DANNO_RADICALE = "danno_radicale";

    // Optional, non String: svernamento_oospore, infezione_secondaria e
    // danno_radicale non hanno alcuna azione catalogata in regola_azione
    // (nessuna fonte bibliografica indica un trattamento per queste tre
    // condizioni), quindi non esiste un tipo_azione valido da scrivere.
    // Optional.empty() dice al chiamante di non creare affatto un
    // trattamento per queste allerte, che restano comunque persistite e
    // risolte come tutte le altre - solo senza un'azione di mitigazione
    // collegata.
    public Optional<String> tipoAzione(AllertaEvent evento) {
        return switch (evento.tipo()) {
            case STRESS_IDRICO -> Optional.of("irrigazione_soccorso");
            case ONDATA_DI_CALORE, SUNBURN -> Optional.of("nebulizzazione");
            case TRE_DIECI -> Optional.of("trattamento_fitosanitario");
            case SVERNAMENTO_OOSPORE, INFEZIONE_SECONDARIA, DANNO_RADICALE -> Optional.empty();
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da MappatoreAzione: " + evento.tipo());
        };
    }

    // Locale.ROOT esplicito: String.formatted() senza argomento usa il locale
    // di default della JVM, che su una macchina con locale italiano
    // renderebbe il separatore decimale una virgola ("45,70") invece del
    // punto atteso altrove nel progetto (es. nel log JSON strutturato di
    // attuatori, dove lo stesso valore e' serializzato come numero nativo,
    // non come stringa formattata). Locale.ROOT garantisce un output
    // deterministico, indipendente dalla macchina che esegue il processo.
    public String note(AllertaEvent evento) {
        return String.format(Locale.ROOT,
                "tipo=%s, livello=%s, parametro=%s, valoreOsservato=%.2f, descrizione=%s",
                evento.tipo(), evento.livelloRischio(), evento.parametro(),
                evento.valoreOsservato(), evento.messaggio());
    }
}