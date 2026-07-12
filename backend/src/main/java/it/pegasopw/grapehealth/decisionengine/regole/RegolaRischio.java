package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;

import java.util.Optional;

public interface RegolaRischio {
    boolean isApplicabile(MisurazioneMessage misurazione);
    Optional<AllertaEvent> valuta(MisurazioneMessage misurazione, StatoRischio stato);
}