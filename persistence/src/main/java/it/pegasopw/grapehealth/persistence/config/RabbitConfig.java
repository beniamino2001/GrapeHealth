package it.pegasopw.grapehealth.persistence.config;

import it.pegasopw.grapehealth.persistence.listener.MisurazionePersistenceListener;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String MQTT_BRIDGE_EXCHANGE = "amq.topic";
    public static final String MISURAZIONI_QUEUE = "grapehealth.persistence.misurazioni";
    public static final String MISURAZIONI_ROUTING_KEY = "grapehealth.#";

    public static final String ALLERTE_EXCHANGE = "grapehealth.allerte";
    public static final String ALLERTE_QUEUE = "grapehealth.persistence.allerte";
    public static final String ALLERTE_ROUTING_KEY = "allerta.#";

    // Senza questa configurazione, il comportamento di default di Spring AMQP
    // su un'eccezione non gestita nel listener è il reinvio indefinito 
    // dello stesso messaggio, non il suo scarto in una coda ispezionabile.
    public static final String DEAD_LETTER_EXCHANGE = "grapehealth.dlx";
    public static final String MISURAZIONI_DLQ = "grapehealth.persistence.misurazioni.dlq";
    public static final String MISURAZIONI_DLQ_ROUTING_KEY = "persistence.misurazioni.dead";
    public static final String ALLERTE_DLQ = "grapehealth.persistence.allerte.dlq";
    public static final String ALLERTE_DLQ_ROUTING_KEY = "persistence.allerte.dead";

    @Bean
    public TopicExchange mqttBridgeExchange() {
        return new TopicExchange(MQTT_BRIDGE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue misurazioniDeadLetterQueue() {
        return QueueBuilder.durable(MISURAZIONI_DLQ).build();
    }

    @Bean
    public Binding misurazioniDeadLetterBinding(Queue misurazioniDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(misurazioniDeadLetterQueue)
                .to(deadLetterExchange)
                .with(MISURAZIONI_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue misurazioniQueue() {
        return QueueBuilder.durable(MISURAZIONI_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MISURAZIONI_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding misurazioniBinding(Queue misurazioniQueue, TopicExchange mqttBridgeExchange) {
        return BindingBuilder.bind(misurazioniQueue)
                .to(mqttBridgeExchange)
                .with(MISURAZIONI_ROUTING_KEY);
    }

    @Bean
    public TopicExchange allertaExchange() {
        return new TopicExchange(ALLERTE_EXCHANGE, true, false);
    }

    @Bean
    public Queue allerteDeadLetterQueue() {
        return QueueBuilder.durable(ALLERTE_DLQ).build();
    }

    @Bean
    public Binding allerteDeadLetterBinding(Queue allerteDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(allerteDeadLetterQueue)
                .to(deadLetterExchange)
                .with(ALLERTE_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue allerteQueue() {
        return QueueBuilder.durable(ALLERTE_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ALLERTE_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding allerteBinding(Queue allerteQueue, TopicExchange allertaExchange) {
        return BindingBuilder.bind(allerteQueue)
                .to(allertaExchange)
                .with(ALLERTE_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(null);
        return converter;
    }

    @Bean
    public SimpleMessageListenerContainer misurazioniListenerContainer(
            ConnectionFactory connectionFactory,
            MisurazionePersistenceListener misurazionePersistenceListener) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(MISURAZIONI_QUEUE);
        container.setMessageListener(misurazionePersistenceListener);
        container.setPrefetchCount(50);
        // Questo container è cablato a mano, non passa dalla container factory
        // autoconfigurata da Spring Boot: le proprietà
        // spring.rabbitmq.listener.simple.* di application.yaml non
        // si applicano qui, va impostato esplicitamente. Nessun retry
        // volutamente: un JSON malformato è un fallimento permanente, non
        // transitorio e ritentarlo non lo risolverebbe, quindi va in
        // dead-letter al primo fallimento invece di essere ritentato a vuoto.
        container.setDefaultRequeueRejected(false);
        return container;
    }
}