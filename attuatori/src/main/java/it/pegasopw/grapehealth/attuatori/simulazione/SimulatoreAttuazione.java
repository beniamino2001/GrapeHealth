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

            // regola_azione cataloga per "sunburn" anche applicazione_caolino, rete_ombreggiante e
            // applicazione_zeolite come strategie di mitigazione alternative alla nebulizzazione.
            // Nessuna fonte bibliografica indica un criterio per scegliere tra loro:
            // SimulatoreAttuazione continua a simulare solo la nebulizzazione, coerentemente con quanto
            // MappatoreAzione fa in persistence per lo stesso motivo.
            case SUNBURN -> severo
                    ? "nebulizzazione anti-scottatura d'emergenza"
                    : "nebulizzazione anti-scottatura";

            // ondata_di_calore resta a soglia singola nel decision engine: nessun
            // "severo" atteso, quindi la rete di sicurezza sotto resta attiva qui.
            case ONDATA_DI_CALORE -> {
                avvisaSeSeveroInatteso(tipo, evento.livelloRischio());
                yield "nebulizzazione anti-calore";
            }

            // tre_dieci prevede adesso due livelli: "moderato" segnala l'infezione
            // primaria appena rilevata (trigger di Baldacci), "severo" segnala che
            // l'incubazione ha raggiunto il 70% e la finestra di trattamento si sta
            // chiudendo.
            case TRE_DIECI -> severo
                    ? "trattamento fitosanitario urgente"
                    : "trattamento fitosanitario mirato";

            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito dal simulatore attuatori: " + tipo);
        };
    }

    // Rete di sicurezza per i soli tipi che, secondo il decision engine attuale,
    // non dovrebbero mai produrre un livello "severo".
    private void avvisaSeSeveroInatteso(String tipo, String livello) {
        if (SEVERO.equals(livello)) {
            log.warn("Ricevuto livello 'severo' non previsto per il tipo '{}': verificare se la regola corrispondente è cambiata.", tipo);
        }
    }
}