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

    public MisurazionePersistenceListener(JsonMapper jsonMapper,
                                          MisurazioneRepository misurazioneRepository,
                                          CacheNodi cacheNodi,
                                          StimaScalaSimulazione stimaScalaSimulazione) {
        this.jsonMapper = jsonMapper;
        this.misurazioneRepository = misurazioneRepository;
        this.cacheNodi = cacheNodi;
        this.stimaScalaSimulazione = stimaScalaSimulazione;
    }

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

        // Aggiorna la stima della scala di simulazione per OGNI misurazione
        // ricevuta, indipendentemente dal nodo: non richiede che il nodo sia
        // noto alla cache, e piu' misurazioni si osservano piu' la stima
        // (media mobile) si stabilizza.
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

        List<MisurazioneEntity> daScrivere = null;
        synchronized (buffer) {
            buffer.add(entita);
            if (buffer.size() >= DIMENSIONE_BATCH) {
                daScrivere = new ArrayList<>(buffer);
                buffer.clear();
            }
        }
        if (daScrivere != null) {
            scriviBatch(daScrivere);
        }
    }

    @Scheduled(fixedDelay = 2000)
    void flushPeriodico() {
        List<MisurazioneEntity> daScrivere = null;
        synchronized (buffer) {
            if (!buffer.isEmpty()) {
                daScrivere = new ArrayList<>(buffer);
                buffer.clear();
            }
        }
        if (daScrivere != null) {
            scriviBatch(daScrivere);
        }
    }

    private void scriviBatch(List<MisurazioneEntity> entita) {
        misurazioneRepository.saveAll(entita);
        log.info("Scritte {} misurazioni in batch", entita.size());
    }

    private void handleStatoNodo(Message rawMessage, String routingKey) {
        String statoNodo = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        String nodo = routingKey.substring(STATUS_ROUTING_PREFIX.length());
        log.info("Stato nodo aggiornato (non persistito, nessuna tabella dedicata): nodo={}, stato={}", nodo, statoNodo);
    }
}