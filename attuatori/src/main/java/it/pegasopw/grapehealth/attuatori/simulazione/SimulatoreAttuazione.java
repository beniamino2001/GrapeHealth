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
    private static final String SVERNAMENTO_OOSPORE = "svernamento_oospore";
    private static final String INFEZIONE_SECONDARIA = "infezione_secondaria";
    private static final String DANNO_RADICALE = "danno_radicale";
    private static final String SEVERO = "severo";
    private static final String NESSUNA_AZIONE_CATALOGATA = "nessuna azione di mitigazione catalogata (monitoraggio)";

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

            // ondata_di_calore ha oggi due livelli: moderato a 35°C (risposta
            // fisiologica della vite allo stress da calore), severo a 40°C
            // (soglia convenzionale da tessuto fogliare su specie diversa da
            // quella misurata in campo, dichiarata come tale nello schema).
            case ONDATA_DI_CALORE -> severo
                    ? "nebulizzazione anti-calore d'emergenza"
                    : "nebulizzazione anti-calore";

            // tre_dieci prevede adesso due livelli: "moderato" segnala l'infezione
            // primaria appena rilevata (trigger di Baldacci), "severo" segnala che
            // l'incubazione ha raggiunto il 70% e la finestra di trattamento si sta
            // chiudendo.
            case TRE_DIECI -> severo
                    ? "trattamento fitosanitario urgente"
                    : "trattamento fitosanitario mirato";

            // Nessuna delle due regole ha un'azione catalogata in regola_azione
            // (v. MappatoreAzione in persistence, stesso motivo): restano solo
            // segnali di monitoraggio, loggati comunque per completare l'audit
            // trail. Entrambe a soglia singola nel decision engine attuale (solo
            // "moderato"), quindi la rete di sicurezza sotto resta attiva.
            case SVERNAMENTO_OOSPORE, INFEZIONE_SECONDARIA -> {
                avvisaSeSeveroInatteso(tipo, evento.livelloRischio());
                yield NESSUNA_AZIONE_CATALOGATA;
            }

            // Stessa assenza di azione catalogata, ma il caso opposto delle due
            // regole sopra: danno_radicale produce sempre e solo "severo" (un
            // danno fisiologico diretto, non un primo segnale), mai "moderato".
            case DANNO_RADICALE -> {
                if (!severo) {
                    log.warn("Ricevuto livello '{}' inatteso per il tipo 'danno_radicale': la regola corrispondente lo produce oggi solo come 'severo'.",
                            evento.livelloRischio());
                }
                yield NESSUNA_AZIONE_CATALOGATA;
            }

            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito dal simulatore attuatori: " + tipo);
        };
    }

    // Rete di sicurezza per i tipi che, secondo il decision engine attuale,
    // non dovrebbero mai produrre un livello "severo".
    private void avvisaSeSeveroInatteso(String tipo, String livello) {
        if (SEVERO.equals(livello)) {
            log.warn("Ricevuto livello 'severo' non previsto per il tipo '{}': verificare se la regola corrispondente è cambiata.", tipo);
        }
    }
}