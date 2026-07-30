package com.auction.agentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String AUCTION_EVENTS_EXCHANGE = "auction.events";
    public static final String BID_PLACED_QUEUE = "agent.bid.placed";
    public static final String BID_PLACED_KEY = "bid.placed";
    public static final String AUCTION_CLOSED_QUEUE = "agent.auction.closed";
    public static final String AUCTION_CLOSED_KEY = "auction.closed";

    @Bean
    TopicExchange auctionEventsExchange() {
        return new TopicExchange(AUCTION_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue bidPlacedQueue() {
        return QueueBuilder
                .durable(BID_PLACED_QUEUE)
                .build();
    }

    @Bean
    Binding bidPlacedBinding(
            @Qualifier("bidPlacedQueue") Queue queue,
            TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(BID_PLACED_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    Queue auctionClosedQueue() {
        return QueueBuilder
                .durable(AUCTION_CLOSED_QUEUE).build();
    }

    @Bean
    Binding auctionClosedBinding(
            @Qualifier("auctionClosedQueue") Queue queue,
            TopicExchange exchange
    ) {
        return BindingBuilder.bind(queue).to(exchange).with(AUCTION_CLOSED_KEY);
    }

}
