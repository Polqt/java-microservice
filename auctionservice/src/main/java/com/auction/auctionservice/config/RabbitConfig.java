package com.auction.auctionservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String AUCTION_EVENTS_EXCHANGE = "auction.events";
    public static final String BID_PLACED_KEY = "bid.placed";
    public static final String AUCTION_CLOSED_KEY = "auction.closed";
    public static final String DEAL_CREATED_KEY = "deal.created";

    @Bean
    TopicExchange auctionEventsExchange() {
        return new TopicExchange(AUCTION_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
