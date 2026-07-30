package com.auction.agentservice.auction;

import java.math.BigDecimal;

public record AgentBidCommand(
        String bidderId,
        BigDecimal amount,
        String reactionId
) {
}
