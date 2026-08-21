package com.auction.auctionservice.dto;

import java.util.List;

/**
 * Our own paging shape, not Spring Data's {@code Page} serialized directly —
 * that format has changed between Spring Data versions and isn't ours to
 * depend on across an API boundary.
 */
public record AuctionPageResponse(
        List<AuctionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
