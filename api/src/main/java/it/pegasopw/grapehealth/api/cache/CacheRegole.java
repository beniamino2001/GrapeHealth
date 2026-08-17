package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.RegolaEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.api.repository.RegolaRepository;
import it.pegasopw.grapehealth.api.repository.RegolaSogliaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheRegole {

    private static final Logger log = LoggerFactory.getLogger(CacheRegole.class);

    private final RegolaRepository regolaRepository;
    private final RegolaSogliaRepository regolaSogliaRepository;
    private final Map<String, RegolaEntity> regolaPerCodice = new ConcurrentHashMap<>();
    private final Map<String, List<RegolaSogliaEntity>> soglieePerRegolaCodice = new ConcurrentHashMap<>();

    public CacheRegole(RegolaRepository regolaRepository, RegolaSogliaRepository regolaSogliaRepository) {
        this.regolaRepository = regolaRepository;
        this.regolaSogliaRepository = regolaSogliaRepository;
    }

    @PostConstruct
    void carica() {
        for (RegolaEntity regola : regolaRepository.findAll()) {
            regolaPerCodice.put(regola.getCodice(), regola);
        }
        for (RegolaSogliaEntity soglia : regolaSogliaRepository.findAll()) {
            soglieePerRegolaCodice.computeIfAbsent(soglia.getRegolaCodice(), k -> new ArrayList<>()).add(soglia);
        }
        log.info("Cache regole caricata: {} regole note, {} soglie bibliografiche",
                regolaPerCodice.size(), regolaSogliaRepository.count());
    }

    public RegolaEntity trovaPerCodice(String codice) {
        return regolaPerCodice.get(codice);
    }

    public List<RegolaSogliaEntity> sogliePerRegola(String regolaCodice) {
        return soglieePerRegolaCodice.getOrDefault(regolaCodice, List.of());
    }
}