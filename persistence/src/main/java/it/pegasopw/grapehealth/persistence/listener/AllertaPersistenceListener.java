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

import java.util.Optional;

@Component
public class AllertaPersistenceListener {

    private static final String STATO_ATTIVA = "attiva";

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

        // A time-scale molto alte lo stesso nodo può ripubblicare la stessa
        // condizione di rischio a pochi secondi reali di distanza - a volte
        // allo stesso livello (il margine di isteresi del decision engine
        // non sempre basta a filtrare il sottocampionamento del segnale
        // simulato), a volte con un cambio di livello quasi immediato
        // (es. moderato seguito da severo entro pochi secondi, prima ancora
        // che il primo si sia risolto). In entrambi i casi un nodo ha un
        // solo livello di rischio corrente per un dato tipo, non due
        // contemporaneamente: si cerca quindi l'eventuale allerta già attiva
        // per lo stesso nodo/tipo, a prescindere dal suo livello.
        Optional<AllertaEntity> allertaAttivaEsistente = allertaRepository
                .findByNodoIdAndTipoAndStato(nodoId, evento.tipo(), STATO_ATTIVA);

        if (allertaAttivaEsistente.isPresent()) {
            AllertaEntity esistente = allertaAttivaEsistente.get();

            if (esistente.getLivelloRischio().equals(evento.livelloRischio())) {
                // Stesso livello: è la stessa condizione ancora in corso, non
                // una nuova allerta. Invece di aprire una riga duplicata (e un
                // secondo trattamento), si estende la pianificazione di
                // risoluzione di quella già attiva.
                schedulerRisoluzioneAllerte.pianifica(esistente);
                log.info("Allerta già attiva per tipo={}, livello={}, nodoId={}: pianificazione di risoluzione estesa (id={})",
                        evento.tipo(), evento.livelloRischio(), nodoId, esistente.getId());
                return;
            }

            // Livello diverso: il rischio per questo nodo è cambiato. Si
            // chiude subito quella al livello precedente invece di lasciarla
            // scadere per conto suo - altrimenti resterebbero visibili come
            // "attive" in contemporanea due allerte a livelli diversi per lo
            // stesso nodo/tipo, che è esattamente il sintomo osservato sulla
            // dashboard. Si prosegue poi sotto per aprire la nuova allerta al
            // nuovo livello, con il proprio trattamento se previsto.
            schedulerRisoluzioneAllerte.risolviOra(esistente);
            log.info("Livello di rischio cambiato da {} a {} per tipo={}, nodoId={}: allerta precedente chiusa (id={})",
                    esistente.getLivelloRischio(), evento.livelloRischio(), evento.tipo(), nodoId, esistente.getId());
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
        Long allertaId = allerta.getId();
        log.info("Allerta persistita: id={}, tipo={}, livello={}, nodoId={}, parcellaId={}",
                allertaId, evento.tipo(), evento.livelloRischio(), nodoId, parcellaId);

        // svernamento_oospore, infezione_secondaria e danno_radicale non hanno
        // un'azione catalogata (v. MappatoreAzione): per questi l'allerta
        // viene comunque persistita e pianificata per la risoluzione come
        // tutte le altre, ma senza un trattamento collegato.
        mappatoreAzione.tipoAzione(evento).ifPresentOrElse(
                tipoAzione -> {
                    TrattamentoEntity trattamento = new TrattamentoEntity(
                            allertaId, tipoAzione, mappatoreAzione.note(evento));
                    trattamentoRepository.save(trattamento);
                    log.info("Trattamento persistito: allertaId={}, tipoAzione={}", allertaId, tipoAzione);
                },
                () -> log.info("Nessuna azione catalogata per tipo={}: solo monitoraggio, allertaId={}",
                        evento.tipo(), allertaId));

        // La risoluzione dell'allerta è pianificata con un ritardo REALE,
        // non applicata qui nella stessa transazione: l'allerta resta "attiva" e visibile
        // a chi consulta l'API in tempo reale per la durata del ritardo. La
        // scadenza pianificata viene anche persistita su
        // allerta.risoluzione_pianificata_il, per poter essere ricostruita
        // a un eventuale riavvio di questo processo.
        schedulerRisoluzioneAllerte.pianifica(allerta);
    }
}