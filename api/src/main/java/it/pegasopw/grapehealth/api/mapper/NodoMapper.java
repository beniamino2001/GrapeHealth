package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.NodoDTO;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;

public final class NodoMapper {
    private NodoMapper() {}

    public static NodoDTO toDTO(NodoSensoreEntity nodo, CacheParcelle cacheParcelle) {
        return new NodoDTO(nodo.getCodice(), nodo.getTipoNodo(),
                cacheParcelle.nomePerId(nodo.getParcellaId()),
                nodo.isAttivo(), nodo.getDataInstallazione());
    }
}