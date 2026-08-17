package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.azione.MappatoreAzione;
import it.pegasopw.grapehealth.persistence.cache.CacheNodi;
import it.pegasopw.grapehealth.persistence.cache.CacheParcelle;
import it.pegasopw.grapehealth.persistence.config.RabbitConfig;
import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.persistence.model.entity.TrattamentoEntity;
import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
import it.pegasopw.grapehealth.persistence.repository.TrattamentoRepository;
import it.pegasopw.grapehealth.persistence.risoluzione.SchedulerRisoluzioneAllerte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AllertaPersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(AllertaPersistenceListener.class);

    private final AllertaRepository allertaRepository;
    private final TrattamentoRepository trattamentoRepository;
    private final CacheNodi cacheNodi;
    private final CacheParcelle cacheParcelle;
    private final MappatoreAzione mappatoreAzione;
    private final SchedulerRisoluzioneAllerte schedulerRisoluzioneAllerte;

    public AllertaPersistenceListener(AllertaRepository allertaRepository,
                                      TrattamentoRepository trattamentoRepository,
                                      CacheNodi cacheNodi,
                                      CacheParcelle cacheParcelle,
                                      MappatoreAzione mappatoreAzione,
                                      SchedulerRisoluzioneAllerte schedulerRisoluzioneAllerte) {
        this.allertaRepository = allertaRepository;
        this.trattamentoRepository = trattamentoRepository;
        this.cacheNodi = cacheNodi;
        this.cacheParcelle = cacheParcelle;
        this.mappatoreAzione = mappatoreAzione;
        this.schedulerRisoluzioneAllerte = schedulerRisoluzioneAllerte;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.ALLERTE_QUEUE)
    public void onAllerta(AllertaEvent evento) {
        Long nodoId = cacheNodi.idPerCodice(evento.nodo());
        if (nodoId == null) {
            log.warn("Nodo sconosciuto '{}', allerta scartata (verificare init_nodi_db.py)", evento.nodo());
            return;
        }

        // A differenza del nodo, una parcella non risolvibile non blocca la
        // scrittura: allerta.parcella_id è nullable e questo campo è un
        // arricchimento (traccia diretta per regole a livello di parcella),
        // non un dato indispensabile come nodo_id lo è in questo modulo.
        Long parcellaId = cacheParcelle.idPerNome(evento.parcella());
        if (parcellaId == null) {
            log.warn("Parcella sconosciuta '{}', allerta persistita senza parcella_id", evento.parcella());
        }

        AllertaEntity allerta = new AllertaEntity(
                evento.tipo(),
                evento.livelloRischio(),
                nodoId,
                parcellaId,
                evento.messaggio(),
                evento.tipo(),
                evento.timestamp());

        allerta = allertaRepository.save(allerta);
        log.info("Allerta persistita: id={}, tipo={}, livello={}, nodoId={}, parcellaId={}",
                allerta.getId(), evento.tipo(), evento.livelloRischio(), nodoId, parcellaId);

        TrattamentoEntity trattamento = new TrattamentoEntity(
                allerta.getId(),
                mappatoreAzione.tipoAzione(evento),
                mappatoreAzione.note(evento));

        trattamentoRepository.save(trattamento);
        log.info("Trattamento persistito: allertaId={}, tipoAzione={}", allerta.getId(), trattamento.getTipoAzione());

        // La risoluzione dell'allerta è pianificata con un ritardo REALE,
        // non applicata qui nella stessa transazione: l'allerta resta "attiva" e visibile
        // a chi consulta l'API in tempo reale per la durata del ritardo. La
        // scadenza pianificata viene anche persistita su
        // allerta.risoluzione_pianificata_il, per poter essere ricostruita
        // a un eventuale riavvio di questo processo.
        schedulerRisoluzioneAllerte.pianifica(allerta);
    }
}