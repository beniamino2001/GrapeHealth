package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.azione.MappatoreAzione;
import it.pegasopw.grapehealth.persistence.cache.CacheNodi;
import it.pegasopw.grapehealth.persistence.config.RabbitConfig;
import it.pegasopw.grapehealth.persistence.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.persistence.model.entity.TrattamentoEntity;
import it.pegasopw.grapehealth.persistence.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.persistence.repository.AllertaRepository;
import it.pegasopw.grapehealth.persistence.repository.TrattamentoRepository;
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
    private final MappatoreAzione mappatoreAzione;

    public AllertaPersistenceListener(AllertaRepository allertaRepository,
                                      TrattamentoRepository trattamentoRepository,
                                      CacheNodi cacheNodi,
                                      MappatoreAzione mappatoreAzione) {
        this.allertaRepository = allertaRepository;
        this.trattamentoRepository = trattamentoRepository;
        this.cacheNodi = cacheNodi;
        this.mappatoreAzione = mappatoreAzione;
    }

    @Transactional
    @RabbitListener(queues = RabbitConfig.ALLERTE_QUEUE)
    public void onAllerta(AllertaEvent evento) {
        Long nodoId = cacheNodi.idPerCodice(evento.nodo());
        if (nodoId == null) {
            log.warn("Nodo sconosciuto '{}', allerta scartata (verificare init_nodi_db.py)", evento.nodo());
            return;
        }

        AllertaEntity allerta = new AllertaEntity(
                evento.tipo(),
                evento.livelloRischio(),
                nodoId,
                evento.messaggio(),
                evento.tipo(),
                evento.timestamp());

        allerta = allertaRepository.save(allerta);
        log.info("Allerta persistita: id={}, tipo={}, livello={}, nodoId={}",
                allerta.getId(), evento.tipo(), evento.livelloRischio(), nodoId);

        TrattamentoEntity trattamento = new TrattamentoEntity(
                allerta.getId(),
                mappatoreAzione.tipoAzione(evento),
                mappatoreAzione.note(evento));

        trattamentoRepository.save(trattamento);
        log.info("Trattamento persistito: allertaId={}, tipoAzione={}", allerta.getId(), trattamento.getTipoAzione());
    }
}