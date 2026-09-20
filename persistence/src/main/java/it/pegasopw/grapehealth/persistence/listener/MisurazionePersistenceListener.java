package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.cache.CacheNodi;
import it.pegasopw.grapehealth.persistence.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.persistence.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.persistence.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.persistence.simulazione.StimaScalaSimulazione;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.locks.ReentrantLock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class MisurazionePersistenceListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MisurazionePersistenceListener.class);
    private static final String STATUS_ROUTING_PREFIX = "grapehealth.status.";
    private static final int DIMENSIONE_BATCH = 50;

    private final JsonMapper jsonMapper;
    private final MisurazioneRepository misurazioneRepository;
    private final CacheNodi cacheNodi;
    private final StimaScalaSimulazione stimaScalaSimulazione;
    private final List<MisurazioneEntity> buffer = new ArrayList<>(DIMENSIONE_BATCH);
    private final ReentrantLock lockScrittura = new ReentrantLock();

    public MisurazionePersistenceListener(JsonMapper jsonMapper,
            MisurazioneRepository misurazioneRepository,
            CacheNodi cacheNodi,
            StimaScalaSimulazione stimaScalaSimulazione) {
        this.jsonMapper = jsonMapper;
        this.misurazioneRepository = misurazioneRepository;
        this.cacheNodi = cacheNodi;
        this.stimaScalaSimulazione = stimaScalaSimulazione;
    }

    // Questa coda riceve anche i messaggi di stato online/offline dei nodi: 
    // vanno solo loggati, non trattati come una misurazione da parsare e salvare.
    @Override
    public void onMessage(Message rawMessage) {
        String routingKey = rawMessage.getMessageProperties().getReceivedRoutingKey();

        if (routingKey != null && routingKey.startsWith(STATUS_ROUTING_PREFIX)) {
            handleStatoNodo(rawMessage, routingKey);
        } else {
            handleMisurazione(rawMessage);
        }
    }

    private void handleMisurazione(Message rawMessage) {
        MisurazioneMessage misurazione = jsonMapper.readValue(rawMessage.getBody(), MisurazioneMessage.class);

        stimaScalaSimulazione.osserva(misurazione.timestampRilevazione());

        Long nodoId = cacheNodi.idPerCodice(misurazione.nodo());
        if (nodoId == null) {
            log.warn("Nodo sconosciuto '{}', misurazione scartata (verificare init_nodi_db.py)", misurazione.nodo());
            return;
        }

        MisurazioneEntity entita = new MisurazioneEntity(
                nodoId,
                misurazione.parametro(),
                misurazione.valore(),
                misurazione.unitaMisura(),
                misurazione.timestampRilevazione());

        boolean batchPronto;
        synchronized (buffer) {
            buffer.add(entita);
            batchPronto = buffer.size() >= DIMENSIONE_BATCH;
        }
        if (batchPronto) {
            provaScriviBatch();
        }
    }

    @Scheduled(fixedDelay = 2000)
    void flushPeriodico() {
        provaScriviBatch();
    }

    // Il lock è indispensabile con questo schema, non opzionale: handleMisurazione()
    // (dal listener RabbitMQ) e flushPeriodico() (dallo scheduler) possono altrimenti
    // leggere lo stesso contenuto del buffer e tentare la scrittura
    // contemporaneamente da due thread diversi.
    private void provaScriviBatch() {
        if (!lockScrittura.tryLock()) {
            // Un'altra chiamata sta già scrivendo: esce subito senza rileggere lo
            // stesso buffer. Il prossimo tentativo (scheduler o listener) lo
            // riprenderà comunque.
            return;
        }
        try {
            List<MisurazioneEntity> daScrivere;
            synchronized (buffer) {
                if (buffer.isEmpty()) {
                    return;
                }
                daScrivere = new ArrayList<>(buffer);
            }

            long attesaMs = 500;
            for (int tentativo = 1; tentativo <= 3; tentativo++) {
                try {
                    misurazioneRepository.saveAll(daScrivere);
                    synchronized (buffer) {
                        buffer.subList(0, Math.min(daScrivere.size(), buffer.size())).clear();
                    }
                    log.info("Scritte {} misurazioni in batch", daScrivere.size());
                    return;
                } catch (RuntimeException e) {
                    if (tentativo == 3) {
                        log.error(
                                "Scrittura batch fallita dopo {} tentativi, {} misurazioni restano nel buffer per il prossimo giro: {}",
                                tentativo, daScrivere.size(), e.getMessage());
                        return;
                    }
                    log.warn("Scrittura batch fallita (tentativo {}/3), nuovo tentativo fra {}ms: {}", tentativo,
                            attesaMs, e.getMessage());
                    try {
                        Thread.sleep(attesaMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    attesaMs *= 2;
                }
            }
        } finally {
            lockScrittura.unlock();
        }
    }

    private void handleStatoNodo(Message rawMessage, String routingKey) {
        String statoNodo = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        String nodo = routingKey.substring(STATUS_ROUTING_PREFIX.length());
        log.info("Stato nodo aggiornato (non persistito, nessuna tabella dedicata): nodo={}, stato={}", nodo,
                statoNodo);
    }
}