package it.pegasopw.grapehealth.persistence.cache;

import it.pegasopw.grapehealth.persistence.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.persistence.repository.NodoSensoreRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// I nodi sensore sono fissi per l'intera sessione simulata, quindi la mappa
// codice->id viene caricata una sola volta all'avvio invece di interrogare
// il DB per ogni misurazione/allerta in arrivo. Usata per risolvere
// evento.nodo() (codice testuale, es. "idrico-A1") in nodo_id.
@Component
public class CacheNodi {

    private static final Logger log = LoggerFactory.getLogger(CacheNodi.class);

    private final NodoSensoreRepository nodoSensoreRepository;
    private final Map<String, Long> idPerCodice = new ConcurrentHashMap<>();

    public CacheNodi(NodoSensoreRepository nodoSensoreRepository) {
        this.nodoSensoreRepository = nodoSensoreRepository;
    }

    @PostConstruct
    void carica() {
        for (NodoSensoreEntity nodo : nodoSensoreRepository.findAll()) {
            idPerCodice.put(nodo.getCodice(), nodo.getId());
        }
        log.info("Cache nodi caricata: {} nodi noti", idPerCodice.size());
    }

    public Long idPerCodice(String codice) {
        return idPerCodice.get(codice);
    }
}