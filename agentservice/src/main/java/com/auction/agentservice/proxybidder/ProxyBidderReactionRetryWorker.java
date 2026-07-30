package com.auction.agentservice.proxybidder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ProxyBidderReactionRetryWorker {

    private final ProxyBidderReactionRepository reactionRepository;
    private final ProxyBidderReactionService reactionService;
    private final int batchSize;

    public ProxyBidderReactionRetryWorker(
            ProxyBidderReactionRepository reactionRepository,
            ProxyBidderReactionService reactionService,
            @Value("${proxy-bidder.retry.batch-size:100}")
            int batchSize
    ) {
        this.reactionRepository = reactionRepository;
        this.reactionService = reactionService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString =
                    "${proxy-bidder.retry.delay-ms:2000}"
    )
    public void retryPendingReactions() {
        List<ProxyBidderReaction> dueReactions =
                reactionRepository.findAllByOutcomeAndNextAttemptAtBefore(
                        ProxyBidderReactionOutcome.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, batchSize)
                );

        for (ProxyBidderReaction reaction : dueReactions) {
            reactionService.retryPending(reaction.getReactionId());
        }
    }
}
