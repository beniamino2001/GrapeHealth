package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.mapper.NodoMapper;
import it.pegasopw.grapehealth.api.model.dto.NodoDTO;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NodiService {
    private final CacheNodi cacheNodi;
    private final CacheParcelle cacheParcelle;

    public NodiService(CacheNodi cacheNodi, CacheParcelle cacheParcelle) {
        this.cacheNodi = cacheNodi;
        this.cacheParcelle = cacheParcelle;
    }

    public List<NodoDTO> tutti() {
        return cacheNodi.tutti().stream().map(n -> NodoMapper.toDTO(n, cacheParcelle)).toList();
    }

    public NodoDTO perCodice(String codice) {
        NodoSensoreEntity nodo = cacheNodi.trovaPerCodice(codice);
        if (nodo == null) throw new RisorsaNonTrovataException("Nessun nodo trovato con codice " + codice);
        return NodoMapper.toDTO(nodo, cacheParcelle);
    }
}