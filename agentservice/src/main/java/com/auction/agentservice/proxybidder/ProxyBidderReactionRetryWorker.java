package com.auction.agentservice.proxybidder;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProxyBidderReactionRetryWorker {

    private final ProxyBidderReactionRepository reactionRepository;
    private final ProxyBidderReactionService reactionService;

    public ProxyBidderReactionRetryWorker(
            ProxyBidderReactionRepository reactionRepository,
            ProxyBidderReactionService reactionService
    ) {
        this.reactionRepository = reactionRepository;
        this.reactionService = reactionService;
    }

    @Scheduled(
            fixedDelayString =
                    "${proxy-bidder.retry.delay-ms:2000}"
    )
    public void retryPendingReactions() {
        List<ProxyBidderReaction> pendingReactions =
                reactionRepository.findAllByOutcome(
                        ProxyBidderReactionOutcome.PENDING
                );

        for (ProxyBidderReaction reaction : pendingReactions) {
            reactionService.retryPending(
                    reaction.getReactionId()
            );
        };
    }
}
