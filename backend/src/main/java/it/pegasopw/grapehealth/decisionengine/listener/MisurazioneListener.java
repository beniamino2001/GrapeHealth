package it.pegasopw.grapehealth.decisionengine.listener;

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

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MisurazioneListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MisurazioneListener.class);
    private static final String STATUS_ROUTING_PREFIX = "grapehealth.status.";

    private final JsonMapper jsonMapper;
    private final List<RegolaRischio> regole;
    private final AllertaPublisher allertaPublisher;
    private final StatoRischio stato;

    public MisurazioneListener(JsonMapper jsonMapper, List<RegolaRischio> regole,
                               AllertaPublisher allertaPublisher, StatoRischio stato) {
        this.jsonMapper = jsonMapper;
        this.regole = regole;
        this.allertaPublisher = allertaPublisher;
        this.stato = stato;
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
        log.info("Ricevuta misurazione: nodo={}, parcella={}, parametro={}, valore={}",
                misurazione.nodo(), misurazione.parcella(), misurazione.parametro(), misurazione.valore());

        regole.forEach(regola -> regola.valuta(misurazione, stato)
                .ifPresent(allertaPublisher::pubblica));
    }

    private void handleStatoNodo(Message rawMessage, String routingKey) {
        String statoNodo = new String(rawMessage.getBody(), StandardCharsets.UTF_8);
        String nodo = routingKey.substring(STATUS_ROUTING_PREFIX.length());
        log.info("Stato nodo aggiornato: nodo={}, stato={}", nodo, statoNodo);
    }
}