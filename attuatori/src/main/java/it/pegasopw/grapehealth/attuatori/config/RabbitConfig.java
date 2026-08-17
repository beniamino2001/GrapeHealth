package it.pegasopw.grapehealth.attuatori.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ALERT_EXCHANGE = "grapehealth.allerte";
    public static final String INPUT_QUEUE = "grapehealth.attuatori.input";
    public static final String INPUT_ROUTING_KEY = "allerta.#";

    // Senza una coda di dead-letter, un tipo di allerta non riconosciuto da
    // SimulatoreAttuazione (IllegalArgumentException) verrebbe reinviato
    // indefinitamente dal comportamento di default di Spring AMQP.
    public static final String DEAD_LETTER_EXCHANGE = "grapehealth.dlx";
    public static final String INPUT_DLQ = "grapehealth.attuatori.input.dlq";
    public static final String INPUT_DLQ_ROUTING_KEY = "attuatori.input.dead";

    @Bean
    public TopicExchange allertaExchange() {
        return new TopicExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
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
    public Binding inputBinding(Queue inputQueue, TopicExchange allertaExchange) {
        return BindingBuilder.bind(inputQueue).to(allertaExchange).with(INPUT_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(null);
        return converter;
    }
}