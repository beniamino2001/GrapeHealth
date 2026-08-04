package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;

import java.util.Map;

public final class AllertaMapper {

    private AllertaMapper() {
    }

    public static AllertaDTO toDTO(AllertaEntity entita, Map<Long, NodoSensoreEntity> nodiPerId) {
        NodoSensoreEntity nodo = entita.getNodoId() != null ? nodiPerId.get(entita.getNodoId()) : null;
        return new AllertaDTO(
                entita.getId(),
                entita.getTipo(),
                entita.getLivelloRischio(),
                nodo != null ? nodo.getCodice() : null,
                nodo != null ? nodo.getParcella() : null,
                entita.getDescrizione(),
                entita.getRegolaScatenante(),
                entita.getGenerataIl(),
                entita.getRisoltaIl(),
                entita.getStato()
        );
    }
}