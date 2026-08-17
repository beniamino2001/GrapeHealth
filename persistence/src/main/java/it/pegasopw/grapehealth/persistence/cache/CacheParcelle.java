package it.pegasopw.grapehealth.persistence.cache;

import it.pegasopw.grapehealth.persistence.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.persistence.repository.ParcellaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Le tre parcelle sono fisse per l'intera sessione simulata, quindi la
// mappa nome->id viene caricata una sola volta all'avvio invece di
// interrogare il DB per ogni allerta in arrivo. Usata per risolvere
// evento.parcella() (nome testuale, es. "parcellaA") in allerta.parcella_id.
@Component
public class CacheParcelle {

    private static final Logger log = LoggerFactory.getLogger(CacheParcelle.class);

    private final ParcellaRepository parcellaRepository;
    private final Map<String, Long> idPerNome = new ConcurrentHashMap<>();

    public CacheParcelle(ParcellaRepository parcellaRepository) {
        this.parcellaRepository = parcellaRepository;
    }

    @PostConstruct
    void carica() {
        for (ParcellaEntity parcella : parcellaRepository.findAll()) {
            idPerNome.put(parcella.getNome(), parcella.getId());
        }
        log.info("Cache parcelle caricata: {} parcelle note", idPerNome.size());
    }

    public Long idPerNome(String nome) {
        return idPerNome.get(nome);
    }
}