package it.pegasopw.grapehealth.decisionengine.publisher;

import it.pegasopw.grapehealth.decisionengine.config.RabbitConfig;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AllertaPublisher {

    private static final Logger log = LoggerFactory.getLogger(AllertaPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public AllertaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void pubblica(AllertaEvent evento) {
        String routingKey = "allerta.%s.%s.%s".formatted(evento.tipo(), evento.parcella(), evento.nodo());
        rabbitTemplate.convertAndSend(RabbitConfig.ALERT_EXCHANGE, routingKey, evento);
        log.info("Allerta pubblicata: tipo={}, livello={}, nodo={}, routingKey={}",
                evento.tipo(), evento.livelloRischio(), evento.nodo(), routingKey);
    }
}