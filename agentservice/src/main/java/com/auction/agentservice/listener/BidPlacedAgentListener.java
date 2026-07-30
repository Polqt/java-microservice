package com.auction.agentservice.listener;

import com.auction.agentservice.config.RabbitConfig;
import com.auction.agentservice.event.BidPlacedEvent;
import com.auction.agentservice.proxybidder.ProxyBidderReactionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BidPlacedAgentListener {

    private final ProxyBidderReactionService reactionService;

    public BidPlacedAgentListener(ProxyBidderReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @RabbitListener(queues = RabbitConfig.BID_PLACED_QUEUE)
    public void onBidPlaced(BidPlacedEvent event) {
        reactionService.reactTo(event);
    }
}
