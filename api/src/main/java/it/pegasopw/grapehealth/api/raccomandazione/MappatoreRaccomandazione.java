package it.pegasopw.grapehealth.api.raccomandazione;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.springframework.stereotype.Component;

@Component
public class MappatoreRaccomandazione {

    private static final String STRESS_IDRICO = "stress_idrico";
    private static final String ONDATA_DI_CALORE = "ondata_di_calore";
    private static final String SUNBURN = "sunburn";
    private static final String TRE_DIECI = "tre_dieci";

    public String azioneConsigliata(AllertaEntity allerta) {
        return switch (allerta.getTipo()) {
            case STRESS_IDRICO -> "irrigazione_soccorso";
            case ONDATA_DI_CALORE, SUNBURN -> "nebulizzazione";
            case TRE_DIECI -> "trattamento_fitosanitario";
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
            default -> throw new IllegalArgumentException(
                    "Tipo di allerta non gestito da MappatoreRaccomandazione: " + allerta.getTipo());
        };
    }
}