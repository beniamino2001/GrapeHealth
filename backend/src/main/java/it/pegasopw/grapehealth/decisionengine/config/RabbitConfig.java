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

    // Coda di servizio per i messaggi che MisurazioneListener non riesce a
    // elaborare (tipicamente JSON malformato in ingresso su INPUT_QUEUE).
    // Senza questa configurazione, il comportamento di default di Spring AMQP
    // su un'eccezione non gestita nel listener è il reinvio indefinito dello
    // stesso messaggio, non il suo scarto in una coda ispezionabile.
    public static final String DEAD_LETTER_EXCHANGE = "grapehealth.dlx";
    public static final String INPUT_DLQ = "grapehealth.decisionengine.input.dlq";
    public static final String INPUT_DLQ_ROUTING_KEY = "decisionengine.input.dead";

    @Bean
    public TopicExchange mqttBridgeExchange() {
        // esplicitamente dichiarato come "esistente", cioè non lo ricrea se già presente
        return new TopicExchange(MQTT_BRIDGE_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        // Lo stesso exchange grapehealth.dlx dichiarato in persistence: se
        // entrambi i moduli girano contro lo stesso broker, RabbitMQ lo
        // dichiara una volta sola e riusa la stessa istanza; le routing key
        // di dead-letter restano comunque distinte per modulo.
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue inputDeadLetterQueue() {
        return QueueBuilder.durable(INPUT_DLQ).build();
    }

    @Bean
    public Binding inputDeadLetterBinding(Queue inputDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(inputDeadLetterQueue)
                .to(deadLetterExchange)
                .with(INPUT_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue inputQueue() {
        return QueueBuilder.durable(INPUT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", INPUT_DLQ_ROUTING_KEY)
                .build();
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
        // Nessun retry volutamente: un JSON malformato è un fallimento
        // permanente, non transitorio e ritentarlo non lo risolverebbe,
        // quindi va in dead-letter al primo fallimento invece di essere
        // ritentato a vuoto. Stesso principio già applicato in persistence.
        container.setDefaultRequeueRejected(false);
        return container;
    }
}