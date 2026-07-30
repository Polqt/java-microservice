package com.auction.agentservice.proxybidder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProxyBidderReactionRepository extends JpaRepository<ProxyBidderReaction, String> {

    Optional<ProxyBidderReaction>
    findByProxyBidderIdAndSourceEventId(
            String proxyBidderId,
            UUID sourceEventId
    );

    List<ProxyBidderReaction> findAllByOutcome(
            ProxyBidderReactionOutcome outcome
    );

    List<ProxyBidderReaction> findAllByOutcomeAndNextAttemptAtBefore(
            ProxyBidderReactionOutcome outcome,
            LocalDateTime dueBefore,
            Pageable pageable
    );
}
