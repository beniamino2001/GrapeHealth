package it.pegasopw.grapehealth.decisionengine.publisher;

import it.pegasopw.grapehealth.decisionengine.config.RabbitConfig;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AllertaPublisherTest {

    @Test
    void costruisceLaRoutingKeyNellOrdineTipoParcellaNodo() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AllertaPublisher publisher = new AllertaPublisher(rabbitTemplate);

        AllertaEvent evento = new AllertaEvent("tre_dieci", "severo", "meteo-C1", "parcellaC",
                "pioggia", 15.5, "test", Instant.now());

        publisher.pubblica(evento);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.ALERT_EXCHANGE),
                eq("allerta.tre_dieci.parcellaC.meteo-C1"),
                eq(evento));
    }

    @Test
    void ricostruisceCorrettamenteLaRoutingKeyPerUnaCombinazioneDiversa() {
        // stesso test del precedente ma con tipo/parcella/nodo diversi, per
        // escludere che il formato sia stato verificato solo su un caso
        // particolare (es. un ordine dei campi accidentalmente corretto)
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        AllertaPublisher publisher = new AllertaPublisher(rabbitTemplate);

        AllertaEvent evento = new AllertaEvent("sunburn", "moderato", "bacca-A1", "parcellaA",
                "temperatura_bacca", 46.0, "test", Instant.now());

        publisher.pubblica(evento);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.ALERT_EXCHANGE),
                eq("allerta.sunburn.parcellaA.bacca-A1"),
                eq(evento));
    }
}