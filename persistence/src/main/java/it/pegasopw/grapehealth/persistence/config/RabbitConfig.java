package it.pegasopw.grapehealth.persistence.config;

import it.pegasopw.grapehealth.persistence.listener.MisurazionePersistenceListener;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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

    @Bean
    public TopicExchange mqttBridgeExchange() {
        return new TopicExchange(MQTT_BRIDGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue misurazioniQueue() {
        return QueueBuilder.durable(MISURAZIONI_QUEUE).build();
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
    public Queue allerteQueue() {
        return QueueBuilder.durable(ALLERTE_QUEUE).build();
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
        return container;
    }
}