package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.decisionengine.repository.ParcellaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache in memoria della lunghezza del germoglio per parcella (tabella
 * "parcella", colonna lunghezza_germoglio_cm), caricata una sola volta
 * all'avvio. Il dato è un valore fenologico rilevato manualmente a sopralluogo
 * periodico, non da un sensore, quindi non cambia a ogni messaggio e non
 * giustifica una query per ogni misurazione elaborata da RegolaTreDieci.
 *
 * Sostituisce la Map<String, Double> hardcoded nella prima versione di
 * RegolaTreDieci, dopo che il dato è stato spostato in parcella.lunghezza_germoglio_cm.
 */
@Component
public class CacheGermogli {

    private static final Logger log = LoggerFactory.getLogger(CacheGermogli.class);

    private final ParcellaRepository parcellaRepository;
    private final Map<String, Double> lunghezzaCmPerParcella = new ConcurrentHashMap<>();

    public CacheGermogli(ParcellaRepository parcellaRepository) {
        this.parcellaRepository = parcellaRepository;
    }

    @PostConstruct
    public void carica() {
        for (ParcellaEntity parcella : parcellaRepository.findAll()) {
            if (parcella.getLunghezzaGermoglioCm() != null) {
                lunghezzaCmPerParcella.put(parcella.getNome(), parcella.getLunghezzaGermoglioCm());
            }
        }
        log.info("Cache germogli caricata: {} parcelle note", lunghezzaCmPerParcella.size());
    }

    /**
     * Restituisce la lunghezza nota del germoglio per la parcella, o 0.0 se la
     * parcella non è nota o non ha ancora un valore registrato — stesso
     * comportamento della precedente Map.getOrDefault: la condizione della
     * regola dei tre dieci non scatta mai per una parcella senza dato
     * fenologico, invece di sollevare un errore.
     */
    public double lunghezzaCm(String nomeParcella) {
        return lunghezzaCmPerParcella.getOrDefault(nomeParcella, 0.0);
    }
}