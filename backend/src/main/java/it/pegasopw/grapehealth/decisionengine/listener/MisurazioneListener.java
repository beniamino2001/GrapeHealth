package it.pegasopw.grapehealth.decisionengine.listener;

import it.pegasopw.grapehealth.decisionengine.cache.CacheNodiAttivi;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.publisher.AllertaPublisher;
import it.pegasopw.grapehealth.decisionengine.regole.RegolaRischio;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Punto di ingresso del decision engine: riceve ogni messaggio pubblicato sul
 * binding "grapehealth.#" (misurazioni dei sensori e messaggi di stato dei
 * nodi) e li smista in base al prefisso della routing key.
 *
 * Le misurazioni da un nodo esplicitamente disattivato in anagrafica
 * (CacheNodiAttivi) vengono scartate prima di raggiungere qualunque regola:
 * un nodo rimosso dalla topologia non deve continuare a generare allerte
 * come se fosse ancora valido. Un nodo sconosciuto (mai sincronizzato in
 * anagrafica) viene invece elaborato normalmente, con solo un avviso nei
 * log, in quanto scartarlo per assenza di dati sarebbe più rischioso che tenerlo,
 * dato che un'anagrafica non ancora sincronizzata è indistinguibile da un
 * nodo davvero rimosso senza questa distinzione esplicita.
 */
@Component
public class MisurazioneListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MisurazioneListener.class);
    private static final String STATUS_ROUTING_PREFIX = "grapehealth.status.";

    // Retry con backoff esponenziale: solo per fallimenti non già intercettati come permanenti
    // da handleMisurazione() (che li rilancia come AmqpRejectAndDontRequeueException,
    // bypassando questo ciclo) [es. un'eccezione di AllertaPublisher se il broker
    // è momentaneamente irraggiungibile durante la pubblicazione di un'allerta in
    // uscita]. Tre tentativi, 0.5s/1s/2s di attesa fra un tentativo e l'altro, poi
    // il messaggio segue comunque la stessa strada di dead-letter già configurata
    // su RabbitConfig.inputQueue().
    private static final int TENTATIVI_MASSIMI = 3;
    private static final long BACKOFF_INIZIALE_MS = 500;

    private final JsonMapper jsonMapper;
    private final List<RegolaRischio> regole;
    private final AllertaPublisher allertaPublisher;
    private final StatoRischio stato;
    private final CacheNodiAttivi cacheNodiAttivi;
    private final Validator validator;

    public MisurazioneListener(JsonMapper jsonMapper, List<RegolaRischio> regole,
            AllertaPublisher allertaPublisher, StatoRischio stato,
            CacheNodiAttivi cacheNodiAttivi, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.regole = regole;
        this.allertaPublisher = allertaPublisher;
        this.stato = stato;
        this.cacheNodiAttivi = cacheNodiAttivi;
        this.validator = validator;
    }

    @Override
    public void onMessage(Message rawMessage) {
        String routingKey = rawMessage.getMessageProperties().getReceivedRoutingKey();

        if (routingKey != null && routingKey.startsWith(STATUS_ROUTING_PREFIX)) {
            handleStatoNodo(rawMessage, routingKey);
        } else {
            handleMisurazioneConRetry(rawMessage);
        }
    }

    private void handleMisurazioneConRetry(Message rawMessage) {
        long attesaMs = BACKOFF_INIZIALE_MS;
        for (int tentativo = 1; tentativo <= TENTATIVI_MASSIMI; tentativo++) {
            try {
                handleMisurazione(rawMessage);
                return;
            } catch (AmqpRejectAndDontRequeueException fallimentoPermanente) {
                // Un payload malformato o non valido non diventa valido ritentando:
                // propaga subito, senza consumare i tentativi sotto.
                throw fallimentoPermanente;
            } catch (RuntimeException fallimentoPossibilmenteTransitorio) {
                if (tentativo == TENTATIVI_MASSIMI) {
                    throw fallimentoPossibilmenteTransitorio;
                }
                log.warn("Tentativo {}/{} fallito, nuovo tentativo fra {}ms: {}",
                        tentativo, TENTATIVI_MASSIMI, attesaMs, fallimentoPossibilmenteTransitorio.toString());
                try {
                    Thread.sleep(attesaMs);
                } catch (InterruptedException interrotto) {
                    Thread.currentThread().interrupt();
                    throw fallimentoPossibilmenteTransitorio;
                }
                attesaMs *= 2;
            }
        }
    }

    private void handleMisurazione(Message rawMessage) {
        MisurazioneMessage misurazione;
        try {
            misurazione = jsonMapper.readValue(rawMessage.getBody(), MisurazioneMessage.class);
        } catch (JacksonException e) {
            // Fallimento permanente, non transitorio: nessuna quantità di retry
            // farebbe diventare valido un payload malformato.
            throw new AmqpRejectAndDontRequeueException("Payload della misurazione non deserializzabile", e);
        }
        var violazioni = validator.validate(misurazione);
        if (!violazioni.isEmpty()) {
            // Stesso principio del ramo sopra: un campo obbligatorio mancante o fuori
            // formato non si risolve ritentando.
            throw new AmqpRejectAndDontRequeueException(
                    "Misurazione non valida", new ConstraintViolationException(violazioni));
        }
        log.info("Ricevuta misurazione: nodo={}, parcella={}, parametro={}, valore={}",
                misurazione.nodo(), misurazione.parcella(), misurazione.parametro(), misurazione.valore());

        Boolean attivo = cacheNodiAttivi.attivo(misurazione.nodo());
        if (Boolean.FALSE.equals(attivo)) {
            log.warn("Misurazione ignorata da nodo disattivato in anagrafica: nodo={}", misurazione.nodo());
            return;
        }
        if (attivo == null) {
            log.warn("Misurazione da nodo non presente in anagrafica: nodo={} (init_nodi_db.py eseguito?)",
                    misurazione.nodo());
        }

        // Ogni regola decide da sé, tramite isApplicabile(), se questa
        // misurazione la riguarda: qui vengono valutate tutte indistintamente.
        regole.forEach(regola -> regola.valuta(misurazione, stato)
                .ifPresent(allertaPublisher::pubblica));
    }

    private void handleStatoNodo(Message rawMessage, String routingKey) {
        String statoConnessione = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        // Non e' il codice di un singolo nodo_sensore: e' il client_id MQTT condiviso
        // dall'intero processo simulatore (v. mqtt_client.py).
        String idConnessione = routingKey.substring(STATUS_ROUTING_PREFIX.length());
        log.info("Stato della connessione simulatore aggiornato: id={}, stato={}", idConnessione, statoConnessione);
    }
}