package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SchedulerRisoluzioneAllerte {

    private static final Logger log = LoggerFactory.getLogger(SchedulerRisoluzioneAllerte.class);

    private final AllertaRepository allertaRepository;
    private final RitardoRisoluzione ritardoRisoluzione;
    private final Map<Long, Instant> scadenzePerAllerta = new ConcurrentHashMap<>();

    public SchedulerRisoluzioneAllerte(AllertaRepository allertaRepository, RitardoRisoluzione ritardoRisoluzione) {
        this.allertaRepository = allertaRepository;
        this.ritardoRisoluzione = ritardoRisoluzione;
    }

    // Ricostruisce all'avvio le pianificazioni pendenti già scritte su
    // allerta.risoluzione_pianificata_il da una sessione precedente del
    // processo, invece di perderle a ogni riavvio.
    @PostConstruct
    void ricostruisciAllAvvio() {
        List<AllertaEntity> pendenti = allertaRepository.findByStatoAndRisoluzionePianificataIlNotNull("attiva");
        for (AllertaEntity allerta : pendenti) {
            scadenzePerAllerta.put(allerta.getId(), allerta.getRisoluzionePianificataIl());
        }
        if (!pendenti.isEmpty()) {
            log.info("Ricostruite {} pianificazioni di risoluzione pendenti dal database", pendenti.size());
        }
    }

    @Transactional
    public void pianifica(AllertaEntity allerta) {
        Instant scadenza = Instant.now().plus(ritardoRisoluzione.perAllerta(allerta.getTipo(), allerta.getLivelloRischio()));
        pianificaAllaScadenza(allerta, scadenza);
    }

    // Package-private: usato anche dai test per impostare scadenze già
    // superate, senza dover attendere il ritardo reale configurato in
    // RitardoRisoluzione.
    @Transactional
    void pianificaAllaScadenza(AllertaEntity allerta, Instant scadenza) {
        scadenzePerAllerta.put(allerta.getId(), scadenza);
        allerta.pianificaRisoluzione(scadenza);
        allertaRepository.save(allerta);
        log.info("Risoluzione pianificata: allertaId={}, prevista alle {}", allerta.getId(), scadenza);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void risolviScadute() {
        Instant adesso = Instant.now();
        List<Long> scadute = new ArrayList<>();
        for (Map.Entry<Long, Instant> voce : scadenzePerAllerta.entrySet()) {
            if (!voce.getValue().isAfter(adesso)) {
                scadute.add(voce.getKey());
            }
        }

        for (Long allertaId : scadute) {
            scadenzePerAllerta.remove(allertaId);
            allertaRepository.findById(allertaId).ifPresentOrElse(allerta -> {
                allerta.risolvi(Instant.now());
                allertaRepository.save(allerta);
                log.info("Allerta risolta: id={}, risoltaIl={}", allerta.getId(), allerta.getRisoltaIl());
            }, () -> log.warn("Allerta pianificata per la risoluzione ma non più presente: id={}", allertaId));
        }
    }
}