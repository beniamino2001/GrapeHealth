package it.pegasopw.grapehealth.attuatori.simulazione;

import it.pegasopw.grapehealth.attuatori.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AllertaDeadLetterTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void unTipoDiAllertaNonRiconosciutoFinisceNellaCodaDiDeadLetterDopoITentativi() {
        String corpo = "{\"tipo\":\"tipo_inesistente\",\"livelloRischio\":\"moderato\",\"nodo\":\"idrico-A1\","
                + "\"parcella\":\"parcellaA\",\"parametro\":\"psi_stem\",\"valoreOsservato\":-1.3,"
                + "\"messaggio\":\"messaggio di test dead-letter\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";

        MessageProperties proprieta = new MessageProperties();
        proprieta.setContentType("application/json");
        Message messaggio = new Message(corpo.getBytes(StandardCharsets.UTF_8), proprieta);

        rabbitTemplate.send("", RabbitConfig.INPUT_QUEUE, messaggio);

        Message inDeadLetter = rabbitTemplate.receive(RabbitConfig.INPUT_DLQ, 8000);

        assertNotNull(inDeadLetter,
                "un tipo di allerta non riconosciuto avrebbe dovuto raggiungere " + RabbitConfig.INPUT_DLQ
                        + " dopo l'esaurimento dei tentativi");
    }
}