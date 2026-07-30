package com.auction.agentservice.proxybidder;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionClosedEvent(
        UUID eventId,
        String auctionId,
        LocalDateTime closedAt
) {
}
