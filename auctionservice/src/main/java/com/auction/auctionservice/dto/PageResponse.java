package com.auction.auctionservice.dto;

import java.util.List;

/** Shared paging shape for result pages that don't already have their own — see AuctionPageResponse. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
