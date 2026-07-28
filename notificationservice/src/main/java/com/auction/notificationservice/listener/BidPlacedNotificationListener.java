package com.auction.notificationservice.listener;

import com.auction.notificationservice.config.RabbitConfig;
import com.auction.notificationservice.dto.BidUpdateMessage;
import com.auction.notificationservice.event.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class BidPlacedNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(BidPlacedNotificationListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public BidPlacedNotificationListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = RabbitConfig.BID_PLACED_QUEUE)
    public void onBidPlaced(BidPlacedEvent event) {
        log.info(
                "Bid placed: eventId={}, auctionId={}, bidId={}, bidderId={}, amount={}",
                event.eventId(),
                event.auctionId(),
                event.bidId(),
                event.bidderId(),
                event.amount()
        );

        BidUpdateMessage message = new BidUpdateMessage(
                event.eventId(),
                event.auctionId(),
                event.bidId(),
                event.amount(),
                event.timestamp()
        );

        messagingTemplate.convertAndSend(
                "/topic/auctions/" + event.auctionId() + "/bids",
                message
        );
    }

}
