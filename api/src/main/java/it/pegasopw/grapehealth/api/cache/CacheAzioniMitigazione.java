package it.pegasopw.grapehealth.api.cache;

import it.pegasopw.grapehealth.api.model.entity.AzioneMitigazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaAzioneEntity;
import it.pegasopw.grapehealth.api.repository.AzioneMitigazioneRepository;
import it.pegasopw.grapehealth.api.repository.RegolaAzioneRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CacheAzioniMitigazione {

    private static final Logger log = LoggerFactory.getLogger(CacheAzioniMitigazione.class);

    private final AzioneMitigazioneRepository azioneMitigazioneRepository;
    private final RegolaAzioneRepository regolaAzioneRepository;
    private final Map<String, AzioneMitigazioneEntity> azionePerCodice = new ConcurrentHashMap<>();
    private final Map<String, List<RegolaAzioneEntity>> azioniPerRegolaCodice = new ConcurrentHashMap<>();

    public CacheAzioniMitigazione(AzioneMitigazioneRepository azioneMitigazioneRepository,
                                  RegolaAzioneRepository regolaAzioneRepository) {
        this.azioneMitigazioneRepository = azioneMitigazioneRepository;
        this.regolaAzioneRepository = regolaAzioneRepository;
    }

    @PostConstruct
    void carica() {
        for (AzioneMitigazioneEntity azione : azioneMitigazioneRepository.findAll()) {
            azionePerCodice.put(azione.getCodice(), azione);
        }
        for (RegolaAzioneEntity regolaAzione : regolaAzioneRepository.findAll()) {
            azioniPerRegolaCodice.computeIfAbsent(regolaAzione.getRegolaCodice(), k -> new ArrayList<>())
                    .add(regolaAzione);
        }
        log.info("Cache azioni di mitigazione caricata: {} azioni, {} associazioni regola->azione",
                azionePerCodice.size(), regolaAzioneRepository.count());
    }

    public AzioneMitigazioneEntity trovaAzionePerCodice(String codice) {
        return azionePerCodice.get(codice);
    }

    public List<RegolaAzioneEntity> azioniPerRegola(String regolaCodice) {
        return azioniPerRegolaCodice.getOrDefault(regolaCodice, List.of());
    }
}