package it.pegasopw.grapehealth.api.service.support;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NodoResolver {

    private final CacheParcelle cacheParcelle;
    private final CacheNodi cacheNodi;

    public NodoResolver(CacheParcelle cacheParcelle, CacheNodi cacheNodi) {
        this.cacheParcelle = cacheParcelle;
        this.cacheNodi = cacheNodi;
    }

    /**
     * Nessuna query al database: entrambe le cache sono caricate una 
     * sola volta all'avvio, la risoluzione è pura composizione in memoria.
     */
    public List<Long> nodoIdsPerParcella(String nomeParcella) {
        if (nomeParcella == null || nomeParcella.isBlank()) {
            return null;
        }
        Long parcellaId = cacheParcelle.idPerNome(nomeParcella);
        if (parcellaId == null) {
            return List.of();
        }
        return cacheNodi.nodoIdsPerParcella(parcellaId);
    }
}