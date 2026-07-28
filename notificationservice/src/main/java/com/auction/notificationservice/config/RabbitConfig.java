package com.auction.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String AUCTION_EVENTS_EXCHANGE = "auction.events";
    public static final String BID_PLACED_QUEUE =
            "notification.bid.placed";
    public static final String BID_PLACED_KEY = "bid.placed";

    public static final String DEAL_CREATED_QUEUE = "notification.deal.created";
    public static final String DEAL_CREATED_KEY = "deal.created";

    public static final String NOTIFICATION_CLOSED_QUEUE =
            "notification.auction.closed";
    public static final String NOTIFICATION_CLOSED_KEY = "auction.closed";


    @Bean
    TopicExchange auctionEventsExchange() {
        return new TopicExchange(
                AUCTION_EVENTS_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue bidPlacedQueue() {
        return QueueBuilder
                .durable(BID_PLACED_QUEUE)
                .build();
    }

    @Bean
    Binding bidPlacedBinding(@Qualifier("bidPlacedQueue") Queue bidPlacedQueue, TopicExchange auctionEventsExchange) {
        return BindingBuilder
                .bind(bidPlacedQueue)
                .to(auctionEventsExchange)
                .with(BID_PLACED_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    Queue dealCreatedQueue() {
        return QueueBuilder
                .durable(DEAL_CREATED_QUEUE).build();
    }

    @Bean
    Binding dealCreatedBinding(@Qualifier("dealCreatedQueue") Queue dealCreatedQueue, TopicExchange auctionEventsExchange) {
        return BindingBuilder
                .bind(dealCreatedQueue)
                .to(auctionEventsExchange)
                .with(DEAL_CREATED_KEY);
    }

    @Bean
    Queue auctionClosedQueue() {
        return QueueBuilder
                .durable(NOTIFICATION_CLOSED_QUEUE).build();
    }

    @Bean
    Binding auctionClosedBinding(@Qualifier("auctionClosedQueue") Queue auctionClosedQueue, TopicExchange auctionEventsExchange) {
        return BindingBuilder
                .bind(auctionClosedQueue)
                .to(auctionEventsExchange)
                .with(NOTIFICATION_CLOSED_KEY);
    }

}
