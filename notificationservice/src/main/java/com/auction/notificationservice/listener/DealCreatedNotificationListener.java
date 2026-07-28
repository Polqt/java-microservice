package com.auction.notificationservice.listener;

import com.auction.notificationservice.config.RabbitConfig;
import com.auction.notificationservice.event.DealCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DealCreatedNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(DealCreatedNotificationListener.class);

    @RabbitListener(queues = RabbitConfig.DEAL_CREATED_QUEUE)
    public void onDealCreated(DealCreatedEvent event) {
        log.info(
                "Deal created: eventId={}, dealId={}, auctionId={}, sellerId={}, winningBidderId={}, finalPrice={}",
                event.eventId(),
                event.dealId(),
                event.auctionId(),
                event.sellerId(),
                event.winningBidderId(),
                event.finalPrice()
        );
    }
}
