package it.pegasopw.grapehealth.api.raccomandazione;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Determina azione consigliata e testo descrittivo a partire da tipo e livello di rischio di
// un'allerta. azioneConsigliata() restituisce il codice macchina, coerente con MappatoreAzione
// nel modulo persistence; testoRaccomandazione() restituisce il testo mostrato in dashboard,
// differenziato per livello di rischio dove la regola ne prevede piu' di uno.
@Component
public class MappatoreRaccomandazione {

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";
    private static final String SVERNAMENTO_OOSPORE = "svernamento_oospore";
    private static final String INFEZIONE_SECONDARIA = "infezione_secondaria";
    private static final String DANNO_RADICALE = "danno_radicale";

    // Optional, non String: svernamento_oospore e infezione_secondaria non hanno alcuna azione
    // catalogata in regola_azione (nessuna fonte bibliografica indica un trattamento per queste
    // due condizioni), quindi non esiste un codice azione valido da restituire. Optional.empty()
    // dice al chiamante che la raccomandazione resta solo informativa - stesso criterio gia'
    // usato da MappatoreAzione in persistence per lo stesso motivo.
    public Optional<String> azioneConsigliata(AllertaEntity allerta) {
        return switch (allerta.getTipo()) {
            case STRESS_IDRICO -> Optional.of("irrigazione_soccorso");
            case ONDATA_DI_CALORE, SUNBURN -> Optional.of("nebulizzazione");
            case TRE_DIECI -> Optional.of("trattamento_fitosanitario");
            case SVERNAMENTO_OOSPORE, INFEZIONE_SECONDARIA, DANNO_RADICALE -> Optional.empty();
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da MappatoreRaccomandazione: " + allerta.getTipo());
        };
    }

    public String testoRaccomandazione(AllertaEntity allerta) {
        String livello = allerta.getLivelloRischio();
        return switch (allerta.getTipo()) {
            case STRESS_IDRICO -> "Livello di stress idrico %s rilevato: si raccomanda l'attivazione dell'irrigazione di soccorso sulla parcella interessata.".formatted(livello);
            case ONDATA_DI_CALORE -> "Ondata di calore di livello %s: si raccomanda la nebulizzazione preventiva per mitigare lo stress termico sulla chioma.".formatted(livello);
            case SUNBURN -> "Rischio scottatura (sunburn) di livello %s sulla bacca: si raccomanda la nebulizzazione di emergenza per abbassare la temperatura superficiale.".formatted(livello);
            case TRE_DIECI -> "Condizioni della regola dei tre dieci verificate, livello di rischio %s: si raccomanda un trattamento fitosanitario %s contro la peronospora.".formatted(livello, "severo".equals(livello) ? "urgente" : "mirato");
            case SVERNAMENTO_OOSPORE -> "Temperatura del suolo nella banda favorevole alla germinazione delle oospore svernanti di Plasmopara viticola: nessuna azione di mitigazione catalogata, condizione di monitoraggio.";
            case DANNO_RADICALE -> "Temperatura del suolo oltre la soglia fisiologica di danno alla sopravvivenza radicale: nessuna azione di mitigazione catalogata (monitoraggio).";
            case INFEZIONE_SECONDARIA -> "Condizioni favorevoli a un'infezione secondaria di peronospora rilevate (bagnatura fogliare prolungata a temperatura favorevole): nessuna azione di mitigazione catalogata, condizione di monitoraggio.";
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da MappatoreRaccomandazione: " + allerta.getTipo());
        };
    }
}