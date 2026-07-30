package com.auction.agentservice.listener;

import com.auction.agentservice.config.RabbitConfig;
import com.auction.agentservice.event.AuctionClosedEvent;
import com.auction.agentservice.proxybidder.ProxyBidderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuctionClosedAgentListener {

    private final ProxyBidderService proxyBidderService;

    public AuctionClosedAgentListener(ProxyBidderService proxyBidderService) {
        this.proxyBidderService = proxyBidderService;
    }

    @RabbitListener(queues = RabbitConfig.AUCTION_CLOSED_QUEUE)
    public void onAuctionClosed(AuctionClosedEvent auctionClosedEvent) {
        proxyBidderService.completeForAuction(auctionClosedEvent.auctionId());
    }
}
