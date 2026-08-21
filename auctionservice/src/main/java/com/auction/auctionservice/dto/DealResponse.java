package com.auction.auctionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimal Deal read — the platform handles no money (ADR-0004), so this is
 * only enough for the two parties to recognise the outcome and agree the
 * price; it carries no contact details. Full Contact Reveal is a separate,
 * larger spec (deal-contact-reveal) — this exists so the product's terminal
 * step (a won Auction produces something a party can actually read) is not
 * left entirely unbuilt.
 */
public record DealResponse(
        String id,
        String auctionId,
        String sellerId,
        String winningBidderId,
        String winningBidId,
        BigDecimal finalPrice,
        LocalDateTime createdAt
) {
}
