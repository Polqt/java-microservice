package com.auction.auctionservice.event;

import com.auction.auctionservice.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BidPlacedRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(BidPlacedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.AUCTION_EVENTS_EXCHANGE,
                RabbitConfig.BID_PLACED_KEY,
                event
        );
    }
}
