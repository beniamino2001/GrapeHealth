package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.NodoSensoreRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Stesso pattern di CacheNodi in persistence: i 9 nodi sono fissi per l'intera sessione
// simulata, caricati una sola volta all'avvio invece che con una query per richiesta.
// A differenza della versione in persistence (che risolve solo codice->id per scrivere
// una FK), qui serve anche l'entita' completa in lettura, piu' l'indice inverso
// parcella->nodi per il filtro di /api/misurazioni.

// Tabella di riferimento fissa per l'intera sessione simulata: caricata una sola volta
// all'avvio invece di essere interrogata a ogni richiesta. ConcurrentHashMap invece di
// HashMap perche' le mappe sono lette concorrentemente da piu' richieste HTTP mentre vengono
// popolate in carica(); nessuna scrittura avviene dopo l'avvio.
@Component
public class CacheNodi {

    private static final Logger log = LoggerFactory.getLogger(CacheNodi.class);

    private final NodoSensoreRepository nodoSensoreRepository;
    private final Map<Long, NodoSensoreEntity> nodoPerId = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> nodoIdsPerParcellaId = new ConcurrentHashMap<>();
    private final Map<String, Long> idPerCodice = new ConcurrentHashMap<>();

    public CacheNodi(NodoSensoreRepository nodoSensoreRepository) {
        this.nodoSensoreRepository = nodoSensoreRepository;
    }

    @PostConstruct
    void carica() {
        for (NodoSensoreEntity nodo : nodoSensoreRepository.findAll()) {
            nodoPerId.put(nodo.getId(), nodo);
            nodoIdsPerParcellaId.computeIfAbsent(nodo.getParcellaId(), k -> new ArrayList<>()).add(nodo.getId());
            idPerCodice.put(nodo.getCodice(), nodo.getId());
        }
        log.info("Cache nodi caricata: {} nodi noti", nodoPerId.size());
    }

    public NodoSensoreEntity trovaPerId(Long id) {
        return id != null ? nodoPerId.get(id) : null;
    }

    public List<Long> nodoIdsPerParcella(Long parcellaId) {
        return nodoIdsPerParcellaId.getOrDefault(parcellaId, List.of());
    }

    public java.util.Collection<NodoSensoreEntity> tutti() {
        return nodoPerId.values();
    }

    public NodoSensoreEntity trovaPerCodice(String codice) {
        Long id = idPerCodice.get(codice);
        return id != null ? nodoPerId.get(id) : null;
    }
}