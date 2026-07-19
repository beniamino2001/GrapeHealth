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

    @Bean
    public TopicExchange allertaExchange() {
        return new TopicExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public Queue inputQueue() {
        return QueueBuilder.durable(INPUT_QUEUE).build();
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