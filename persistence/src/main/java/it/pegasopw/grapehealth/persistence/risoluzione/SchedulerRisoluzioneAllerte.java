package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
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

// Pianifica la risoluzione delle allerte con un ritardo reale,
// disaccoppiata dalla scrittura del trattamento: evita il problema del dominio
// temporale simulato, e da' alla dashboard/API una finestra concreta in cui 
// l'allerta e' visibile come "attiva".
// Lo stato dunque è mantenuto in memoria, non su una colonna aggiuntiva dello schema: 
// un riavvio del processo perde le pianificazioni in corso, lasciando quelle
// allerte "attiva" indefinitamente.
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

    public void pianifica(Long allertaId, String tipo, String livelloRischio) {
        Instant scadenza = Instant.now().plus(ritardoRisoluzione.perAllerta(tipo, livelloRischio));
        pianificaAllaScadenza(allertaId, scadenza);
    }

    // Package-private: usato anche dai test per impostare scadenze gia' superate,
    // senza dover attendere il ritardo reale configurato in RitardoRisoluzione.
    void pianificaAllaScadenza(Long allertaId, Instant scadenza) {
        scadenzePerAllerta.put(allertaId, scadenza);
        log.info("Risoluzione pianificata: allertaId={}, prevista alle {}", allertaId, scadenza);
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
            }, () -> log.warn("Allerta pianificata per la risoluzione ma non piu' presente: id={}", allertaId));
        }
    }
}