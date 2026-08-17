package it.pegasopw.grapehealth.persistence.azione;

import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MappatoreAzione {

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";

    public String tipoAzione(AllertaEvent evento) {
        return switch (evento.tipo()) {
            case STRESS_IDRICO -> "irrigazione_soccorso";
            case ONDATA_DI_CALORE, SUNBURN -> "nebulizzazione";
            case TRE_DIECI -> "trattamento_fitosanitario";
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da MappatoreAzione: " + evento.tipo());
        };
    }

    // Locale.ROOT esplicito: String.formatted() senza argomento usa il locale
    // di default della JVM, che garantisce un output deterministico,
    // indipendente dalla macchina che esegue il processo.
    public String note(AllertaEvent evento) {
        return String.format(Locale.ROOT,
                "tipo=%s, livello=%s, parametro=%s, valoreOsservato=%.2f, descrizione=%s",
                evento.tipo(), evento.livelloRischio(), evento.parametro(),
                evento.valoreOsservato(), evento.messaggio());
    }
}