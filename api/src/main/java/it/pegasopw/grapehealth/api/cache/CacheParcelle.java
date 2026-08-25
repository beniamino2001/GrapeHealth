package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.api.repository.ParcellaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Tabella di riferimento fissa per l'intera sessione simulata: caricata una sola volta
// all'avvio invece di essere interrogata a ogni richiesta. ConcurrentHashMap invece di
// HashMap perche' le mappe sono lette concorrentemente da piu' richieste HTTP mentre vengono
// popolate in carica(); nessuna scrittura avviene dopo l'avvio.
@Component
public class CacheParcelle {

    private static final Logger log = LoggerFactory.getLogger(CacheParcelle.class);

    private final ParcellaRepository parcellaRepository;
    private final Map<Long, ParcellaEntity> parcellaPerId = new ConcurrentHashMap<>();
    private final Map<String, Long> idPerNome = new ConcurrentHashMap<>();

    public CacheParcelle(ParcellaRepository parcellaRepository) {
        this.parcellaRepository = parcellaRepository;
    }

    @PostConstruct
    void carica() {
        for (ParcellaEntity parcella : parcellaRepository.findAll()) {
            parcellaPerId.put(parcella.getId(), parcella);
            idPerNome.put(parcella.getNome(), parcella.getId());
        }
        log.info("Cache parcelle caricata: {} parcelle note", parcellaPerId.size());
    }

    public Long idPerNome(String nome) {
        return idPerNome.get(nome);
    }

    public String nomePerId(Long id) {
        ParcellaEntity parcella = id != null ? parcellaPerId.get(id) : null;
        return parcella != null ? parcella.getNome() : null;
    }

    public java.util.Collection<ParcellaEntity> tutte() {
        return parcellaPerId.values();
    }

    public ParcellaEntity trovaPerNome(String nome) {
        Long id = idPerNome.get(nome);
        return id != null ? parcellaPerId.get(id) : null;
    }
}