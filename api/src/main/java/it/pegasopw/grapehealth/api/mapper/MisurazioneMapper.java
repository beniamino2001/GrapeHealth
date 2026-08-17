package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;

public final class MisurazioneMapper {

    private MisurazioneMapper() {
    }

    public static MisurazioneDTO toDTO(MisurazioneEntity entita, CacheNodi cacheNodi, CacheParcelle cacheParcelle) {
        NodoSensoreEntity nodo = cacheNodi.trovaPerId(entita.getNodoId());
        String parcellaNome = nodo != null ? cacheParcelle.nomePerId(nodo.getParcellaId()) : null;
        return new MisurazioneDTO(
                entita.getId(),
                nodo != null ? nodo.getCodice() : null,
                parcellaNome,
                entita.getParametro(),
                entita.getValore(),
                entita.getUnitaMisura(),
                entita.getRilevatoIl(),
                entita.getRicevutoIl()
        );
    }
}