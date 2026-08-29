package it.pegasopw.grapehealth.decisionengine.listener;

import it.pegasopw.grapehealth.decisionengine.cache.CacheNodiAttivi;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.publisher.AllertaPublisher;
import it.pegasopw.grapehealth.decisionengine.regole.RegolaRischio;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.stereotype.Component;
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
 * Implementa MessageListener a basso livello invece di usare l'annotazione
 * 
 * @RabbitListener: quest'ultima invoca comunque il MessageConverter
 *                  configurato prima ancora di consegnare il messaggio al
 *                  metodo annotato,
 *                  indipendentemente dal tipo del parametro dichiarato —
 *                  tentando quindi di
 *                  deserializzare come JSON anche i messaggi di stato dei nodi,
 *                  che sono testo
 *                  semplice ("online"/"offline"), e fallendo. Implementando
 *                  l'interfaccia
 *                  direttamente, la conversione JSON avviene solo
 *                  esplicitamente in
 *                  handleMisurazione, mai su handleStatoNodo.
 *
 *                  Le misurazioni da un nodo esplicitamente disattivato in
 *                  anagrafica
 *                  (CacheNodiAttivi) vengono scartate prima di raggiungere
 *                  qualunque regola:
 *                  un nodo rimosso dalla topologia non deve continuare a
 *                  generare allerte
 *                  come se fosse ancora valido. Un nodo sconosciuto (mai
 *                  sincronizzato in
 *                  anagrafica) viene invece elaborato normalmente, con solo un
 *                  avviso nei
 *                  log — scartarlo per assenza di dati sarebbe più rischioso
 *                  che tenerlo,
 *                  dato che un'anagrafica non ancora sincronizzata è
 *                  indistinguibile da un
 *                  nodo davvero rimosso senza questa distinzione esplicita.
 */
@Component
public class MisurazioneListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MisurazioneListener.class);
    private static final String STATUS_ROUTING_PREFIX = "grapehealth.status.";

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
            handleMisurazione(rawMessage);
        }
    }

    private void handleMisurazione(Message rawMessage) {
        MisurazioneMessage misurazione = jsonMapper.readValue(rawMessage.getBody(), MisurazioneMessage.class);
        var violazioni = validator.validate(misurazione);
        if (!violazioni.isEmpty()) {
            // Stesso trattamento del JSON malformato: propagare, non
            // ingoiare, cosicché il dead-letter di RabbitConfig lo intercetti
            // invece di farlo rientrare in una regola con un campo nullo.
            throw new ConstraintViolationException(violazioni);
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
        // dall'intero processo simulatore (v. mqtt_client.py) - un solo segnale per
        // tutti i nodi contemporaneamente, non un'anagrafica di stato per-nodo.
        String idConnessione = routingKey.substring(STATUS_ROUTING_PREFIX.length());
        log.info("Stato della connessione simulatore aggiornato: id={}, stato={}", idConnessione, statoConnessione);
    }
}