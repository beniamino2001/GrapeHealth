package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Svernamento delle oospore di Plasmopara viticola nel terreno: la
 * germinazione avviene in una banda di temperatura del suolo, non oltre una
 * singola soglia direzionale — sotto il confine minimo il patogeno resta
 * quiescente, sopra il confine massimo la germinazione è inibita (Si Ammour
 * et al., 2020). Entrambi i confini letti a runtime da regola_soglia (stesso
 * livello "moderato", distinti dall'operatore: ">=" per il minimo, "<=" per
 * il massimo). Fenomeno distinto dal trigger di Baldacci di RegolaTreDieci:
 * descrive lo svernamento invernale del patogeno, non l'infezione primaria
 * di inizio stagione. Un solo livello di rischio, nessuna soglia "severo"
 * riportata dalla fonte.
 *
 * Isteresi di 1°C su entrambi i confini della banda — non presente come
 * colonna in regola_soglia, resta costante Java. Aggiunta dopo che una
 * ricalibrazione del simulatore (per rendere raggiungibile la soglia di
 * RegolaDannoRadicale) ha tenuto per diverse ore al giorno il valore a
 * ridosso del confine superiore di questa banda, producendo oscillazioni
 * osservabili nel numero di allerte reali — corrette a monte con un picco
 * più stretto, ma l'isteresi qui resta una protezione indipendente da quella
 * calibrazione.
 */
@Component
public class RegolaSvernamentoOospore implements RegolaRischio {

    private static final String TIPO = "svernamento_oospore";
    private static final String PARAMETRO = "temperatura_suolo";
    private static final double ISTERESI = 1.0;

    private final double sogliaMinima;
    private final double sogliaMassima;

    public RegolaSvernamentoOospore(CacheSoglieRegole cacheSoglieRegole) {
        this.sogliaMinima = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "moderato", ">=").getValoreSoglia();
        this.sogliaMassima = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO, "moderato", "<=").getValoreSoglia();
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return PARAMETRO.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        String chiave = TIPO + ":" + m.nodo();
        boolean livelloGiaAttivo = stato.livelloRischio(chiave) != null;
        boolean dentroLaBanda = m.valore() >= sogliaMinima && m.valore() <= sogliaMassima;
        boolean dentroLaBandaConIsteresi = m.valore() >= sogliaMinima - ISTERESI
                && m.valore() <= sogliaMassima + ISTERESI;
        boolean condizioneVerificata = dentroLaBanda || (livelloGiaAttivo && dentroLaBandaConIsteresi);

        String livelloPrecedente = stato.livelloRischio(chiave);

        if (condizioneVerificata && livelloPrecedente == null) {
            stato.aggiornaLivelloRischio(chiave, "moderato");
            return Optional.of(new AllertaEvent(
                    TIPO, "moderato", m.nodo(), m.parcella(), m.parametro(), m.valore(),
                    "Temperatura del suolo a %.1f°C, nella banda favorevole alla germinazione delle oospore svernanti (%.0f-%.0f°C)"
                            .formatted(m.valore(), sogliaMinima, sogliaMassima),
                    m.timestampRilevazione()));
        }

        if (!condizioneVerificata && livelloPrecedente != null) {
            stato.aggiornaLivelloRischio(chiave, null);
        }

        return Optional.empty();
    }
}