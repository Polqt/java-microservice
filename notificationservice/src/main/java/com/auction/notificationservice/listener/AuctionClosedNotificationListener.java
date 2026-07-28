package com.auction.notificationservice.listener;

import com.auction.notificationservice.config.RabbitConfig;
import com.auction.notificationservice.event.AuctionClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuctionClosedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AuctionClosedNotificationListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AuctionClosedNotificationListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_CLOSED_QUEUE)
    public void onAuctionClosed(AuctionClosedEvent event) {
        log.info(
                "Auction closed: eventId={}, auctionId={}, closedAt={}",
                event.eventId(),
                event.auctionId(),
                event.closedAt()
        );

        messagingTemplate.convertAndSend(
                "/topic/auctions/" + event.auctionId() + "/closed",
                event

        );
    }
}
