package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.repository.NodoSensoreRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache in memoria dello stato attivo/disattivo di ogni nodo (nodo_sensore),
 * caricata una sola volta all'avvio. init_nodi_db.py (sensors-simulator)
 * disattiva un nodo rimosso da config/nodi.yaml, ma lo fa in un processo
 * separato dal ciclo di pubblicazione di main.py: un nodo appena disattivato
 * potrebbe quindi ancora pubblicare per una finestra di tempo se il
 * simulatore non è stato riavviato in sincronia — questa cache non elimina
 * quella finestra, resta un limite dichiarato, coerente con l'aggiornamento
 * "una tantum all'avvio" già usato da CacheGermogli/CacheTabellaGoidanich.
 */
@Component
public class CacheNodiAttivi {

    private static final Logger log = LoggerFactory.getLogger(CacheNodiAttivi.class);

    private final NodoSensoreRepository nodoSensoreRepository;
    private final Map<String, Boolean> attivoPerCodice = new ConcurrentHashMap<>();

    public CacheNodiAttivi(NodoSensoreRepository nodoSensoreRepository) {
        this.nodoSensoreRepository = nodoSensoreRepository;
    }

    @PostConstruct
    void carica() {
        nodoSensoreRepository.findAll().forEach(nodo ->
                attivoPerCodice.put(nodo.getCodice(), Boolean.TRUE.equals(nodo.getAttivo())));
        long attivi = attivoPerCodice.values().stream().filter(Boolean::booleanValue).count();
        log.info("Cache nodi attivi caricata: {} nodi noti, {} attivi", attivoPerCodice.size(), attivi);
    }

    /**
     * true se il nodo è esplicitamente marcato attivo, false se è
     * esplicitamente disattivato, null se non è presente in anagrafica al
     * momento del caricamento. Un nodo sconosciuto non va trattato come
     * disattivato: potrebbe essere un nodo genuino la cui sincronizzazione
     * (init_nodi_db.py) non ha ancora avuto luogo, non un dato da scartare
     * silenziosamente — il chiamante decide come trattare questo caso.
     */
    public Boolean attivo(String codiceNodo) {
        return attivoPerCodice.get(codiceNodo);
    }
}