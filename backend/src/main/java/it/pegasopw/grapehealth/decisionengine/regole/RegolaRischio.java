package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;

import java.util.Optional;

/**
 * Contratto comune alle quattro regole di rischio del decision engine
 * (stress idrico, ondata di calore, tre dieci, sunburn). Ogni implementazione
 * osserva un parametro specifico (psi_stem, temperatura_aria, ecc.) e decide
 * se una misurazione in arrivo comporta un'allerta, tenendo conto dello stato
 * accumulato nel tempo (isteresi, finestre mobili, episodi) tramite
 * StatoRischio, condiviso fra tutte le regole.
 */
public interface RegolaRischio {

    /**
     * Indica se questa regola osserva il parametro della misurazione data.
     * Ogni implementazione la invoca all'inizio del proprio valuta() come
     * guardia d'ingresso: non è pensata per essere chiamata dall'esterno
     * (il chiamante — MisurazioneListener — passa ogni misurazione a tutte
     * le regole indistintamente, lasciando a ciascuna la decisione).
     */
    boolean isApplicabile(MisurazioneMessage misurazione);

    /**
     * Valuta la misurazione contro le soglie della regola e lo stato
     * accumulato finora. Restituisce un evento solo quando il livello di
     * rischio cambia rispetto all'ultimo pubblicato per la stessa chiave
     * (nodo o parcella, a seconda della regola): Optional.empty() quando la
     * misurazione non è di competenza di questa regola, oppure quando la
     * condizione osservata non produce una transizione di livello.
     */
    Optional<AllertaEvent> valuta(MisurazioneMessage misurazione, StatoRischio stato);
}