package it.pegasopw.grapehealth.decisionengine.config;

import it.pegasopw.grapehealth.decisionengine.listener.MisurazioneListener;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

@Configuration
public class RabbitConfig {

    // Exchange già creato da RabbitMQ per il bridge MQTT->AMQP
    public static final String MQTT_BRIDGE_EXCHANGE = "amq.topic";

    // Coda di ingresso del backend decisionengine
    public static final String INPUT_QUEUE = "grapehealth.decisionengine.input";
    public static final String INPUT_ROUTING_KEY = "grapehealth.#";

    // Exchange ed eventi in uscita
    public static final String ALERT_EXCHANGE = "grapehealth.allerte";

    @Bean
    public TopicExchange mqttBridgeExchange() {
        // esplicitamente dichiarato come "esistente", cioè non lo ricrea se già presente
        return new TopicExchange(MQTT_BRIDGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue inputQueue() {
        return QueueBuilder.durable(INPUT_QUEUE).build();
    }

    @Bean
    public Binding inputBinding(Queue inputQueue, TopicExchange mqttBridgeExchange) {
        return BindingBuilder.bind(inputQueue)
                .to(mqttBridgeExchange)
                .with(INPUT_ROUTING_KEY);
    }

    @Bean
    public StatoRischio statoRischio() {
        return new StatoRischio();
    }

    @Bean
    public TopicExchange allertaExchange() {
        return new TopicExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        // evita che Spring AMQP inserisca header __TypeId__ legati a classi Java, in quanto il producer è Python e non deve conoscere i package
        converter.setClassMapper(null);
        return converter;
    }

    @Bean
    public SimpleMessageListenerContainer misurazioneListenerContainer(
            ConnectionFactory connectionFactory,
            MisurazioneListener misurazioneListener) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(INPUT_QUEUE);
        container.setMessageListener(misurazioneListener);
        container.setPrefetchCount(10); // coerente con quanto definito precedentemente in application.yaml
        return container;
    }
}