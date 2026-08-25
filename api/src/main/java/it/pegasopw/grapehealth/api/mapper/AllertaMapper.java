package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;

public final class AllertaMapper {

    private AllertaMapper() {
    }

    // A differenza di MisurazioneMapper, qui la parcella si risolve direttamente
    // da allerta.parcellaId, senza passare dal nodo.
    public static AllertaDTO toDTO(AllertaEntity entita, CacheNodi cacheNodi, CacheParcelle cacheParcelle) {
        NodoSensoreEntity nodo = cacheNodi.trovaPerId(entita.getNodoId());
        String parcellaNome = cacheParcelle.nomePerId(entita.getParcellaId());
        return new AllertaDTO(
                entita.getId(),
                entita.getTipo(),
                entita.getLivelloRischio(),
                nodo != null ? nodo.getCodice() : null,
                parcellaNome,
                entita.getDescrizione(),
                entita.getRegolaCodice(),
                entita.getGenerataIl(),
                entita.getRisoltaIl(),
                entita.getStato(),
                entita.getRisoluzionePianificataIl());
    }
}