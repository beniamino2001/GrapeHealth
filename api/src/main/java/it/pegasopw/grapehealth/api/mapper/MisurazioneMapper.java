package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;

import java.util.Map;

/**
 * Le entita' di questo modulo non hanno relazioni JPA verso nodo_sensore,
 * quindi l'arricchimento con codice/parcella del nodo va fatto durante l'inizializzazione
 * di questa classe partendo da una mappa nodoId -> entita' precaricata in blocco
 * dal chiamante (una sola query per pagina, non una per riga: evita N+1 query).
 */
public final class MisurazioneMapper {

    private MisurazioneMapper() {
    }

    public static MisurazioneDTO toDTO(MisurazioneEntity entita, Map<Long, NodoSensoreEntity> nodiPerId) {
        NodoSensoreEntity nodo = nodiPerId.get(entita.getNodoId());
        return new MisurazioneDTO(
                entita.getId(),
                nodo != null ? nodo.getCodice() : null,
                nodo != null ? nodo.getParcella() : null,
                entita.getParametro(),
                entita.getValore(),
                entita.getUnitaMisura(),
                entita.getRilevatoIl(),
                entita.getRicevutoIl()
        );
    }
}