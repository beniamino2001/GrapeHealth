package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.regole.support.RegolaSogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.regole.support.SogliaConIsteresi;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

/**
 * Ondata di calore da temperatura dell'aria. Due livelli letti a runtime da
 * regola_soglia:
 *
 * - Moderato: soglia a cui la vite attiva le proprie risposte fisiologiche
 *   allo stress da calore — calo di acidità titolabile, antociani e composti
 *   aromatici, aumento del rischio di sunburn.
 *
 * - Severo: soglia convenzionale, derivata da Luo et al. (2011) su
 *   temperatura fogliare/tessuto in laboratorio (V. amurensis, non V. vinifera)
 *   — non dalla temperatura dell'aria misurata in campo da questo sensore.
 *   Le due grandezze non coincidono e nessuna mappatura calibrata tra le due
 *   è disponibile in letteratura: lo schema stesso la dichiara "convenzionale"
 *   nel campo note. Il livello "severo" prodotto da questa regola resta
 *   quindi il segnale che la temperatura ha superato anche questa soglia
 *   convenzionale, non una certezza di danno fisiologico dimostrabile dal
 *   dato di campo con la stessa solidità del livello "moderato".
 *
 * Isteresi di 1°C condivisa da entrambe le soglie — non presente come
 * colonna in regola_soglia, resta costante Java.
 */
@Component
public class RegolaOndataDiCalore extends RegolaSogliaConIsteresi {

    private static final String TIPO = "ondata_di_calore";
    private static final String PARAMETRO = "temperatura_aria";
    private static final double ISTERESI = 1.0;

    public RegolaOndataDiCalore(CacheSoglieRegole cacheSoglieRegole) {
        super(TIPO, PARAMETRO, SogliaConIsteresi.dueSoglie(
                cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "moderato").getValoreSoglia(),
                cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "severo").getValoreSoglia(),
                ISTERESI, SogliaConIsteresi.Verso.PEGGIORA_SALENDO));
    }

    @Override
    protected String messaggio(MisurazioneMessage m, StatoRischio stato, String livello) {
        double soglia = "severo".equals(livello) ? soglie().sogliaSevero() : soglie().sogliaModerato();
        return "Temperatura dell'aria a %.1f°C, sopra la soglia di %.0f°C (livello %s — attivazione mitigazione)"
                .formatted(m.valore(), soglia, livello);
    }
}