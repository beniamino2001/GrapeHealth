package it.pegasopw.grapehealth.attuatori.simulazione;

import it.pegasopw.grapehealth.attuatori.model.evento.AllertaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatoreAttuazione {

    private static final Logger log = LoggerFactory.getLogger(SimulatoreAttuazione.class);

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";
    private static final String SEVERO = "severo";

    public String determinaAzione(AllertaEvent evento) {
        String tipo = evento.tipo();
        boolean severo = SEVERO.equals(evento.livelloRischio());

        return switch (tipo) {
            case STRESS_IDRICO -> severo
                    ? "irrigazione di soccorso d'emergenza"
                    : "irrigazione di soccorso";

            case SUNBURN -> severo
                    ? "nebulizzazione anti-scottatura d'emergenza"
                    : "nebulizzazione anti-scottatura";

            // ondata_di_calore e tre_dieci non prevedono un livello "severo" nel backend (decision engine)
            case ONDATA_DI_CALORE -> {
                avvisaSeSeveroInatteso(tipo, evento.livelloRischio());
                yield "nebulizzazione anti-calore";
            }
            case TRE_DIECI -> {
                avvisaSeSeveroInatteso(tipo, evento.livelloRischio());
                yield "trattamento fitosanitario mirato";
            }

            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito dal simulatore attuatori: " + tipo);
        };
    }

    // Se in futuro il decision engine introducesse un livello severo per uno di questi due tipi 
    // senza che il simulatore venga aggiornato di conseguenza, viene stampato  un log di avviso.
    private void avvisaSeSeveroInatteso(String tipo, String livello) {
        if (SEVERO.equals(livello)) {
            log.warn("Ricevuto livello 'severo' non previsto per il tipo '{}': verificare se la regola corrispondente è cambiata.", tipo);
        }
    }
}