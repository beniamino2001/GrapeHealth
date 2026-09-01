package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class MisurazioniDeadLetterTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // A differenza della coda allerte, questo container non passa dalla
    // retry policy di Spring Boot (RabbitConfig, nota su misurazioniListenerContainer):
    // un JSON malformato va in dead-letter al primo tentativo, non dopo
    // il backoff — timeout più corto sufficiente, mantenuto uguale per
    // uniformità con l'altro test di dead-letter del modulo.
    @Test
    void jsonMalformatoSuMisurazioniFinisceNellaCodaDiDeadLetter() {
        String corpoNonValido = "{ questo non e' un json valido";

        MessageProperties proprieta = new MessageProperties();
        proprieta.setContentType("application/json");
        Message messaggio = new Message(corpoNonValido.getBytes(StandardCharsets.UTF_8), proprieta);

        rabbitTemplate.send("", RabbitConfig.MISURAZIONI_QUEUE, messaggio);

        Message inDeadLetter = rabbitTemplate.receive(RabbitConfig.MISURAZIONI_DLQ, 8000);

        assertNotNull(inDeadLetter,
                "un JSON malformato avrebbe dovuto raggiungere " + RabbitConfig.MISURAZIONI_DLQ
                        + " invece di bloccare il container o essere reinviato all'infinito");
    }
}