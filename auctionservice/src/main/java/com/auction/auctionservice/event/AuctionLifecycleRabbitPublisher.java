package com.auction.auctionservice.event;

import com.auction.auctionservice.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionLifecycleRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAuctionClosed(AuctionClosedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.AUCTION_EVENTS_EXCHANGE,
                RabbitConfig.AUCTION_CLOSED_KEY,
                event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishDealCreated(DealCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.AUCTION_EVENTS_EXCHANGE,
                RabbitConfig.DEAL_CREATED_KEY,
                event
        );
    }
}
