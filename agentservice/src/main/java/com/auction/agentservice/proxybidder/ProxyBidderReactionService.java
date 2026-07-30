package com.auction.agentservice.proxybidder;

import com.auction.agentservice.auction.*;
import com.auction.agentservice.event.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;


@Service
public class ProxyBidderReactionService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    ProxyBidderReactionService.class
            );

    private final ProxyBidderRepository repository;
    private final AuctionBidStateClient stateClient;
    private final AuctionBidCommandClient commandClient;
    private final ProxyBidderReactionRepository reactionRepository;

    private final int maxAttempts;
    private final Duration retryBaseDelay;

    public ProxyBidderReactionService(
            ProxyBidderRepository repository,
            AuctionBidStateClient stateClient,
            AuctionBidCommandClient commandClient,
            ProxyBidderReactionRepository reactionRepository,
            @Value("${proxy-bidder.retry.max-attempts:3}")
            int maxAttempts,
            @Value("${proxy-bidder.retry.delay-ms:2000}")
            long retryBaseDelayMs
    ) {
        this.repository = repository;
        this.stateClient = stateClient;
        this.commandClient = commandClient;
        this.reactionRepository = reactionRepository;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = Duration.ofMillis(retryBaseDelayMs);
    }

    private void recordCommandOutcome(ProxyBidderReaction reaction, Runnable sendCommand) {
        try {
            sendCommand.run();
            reaction.markSucceeded();
        } catch (HttpClientErrorException.Conflict exception) {
            reaction.markBalked();
        } catch (HttpClientErrorException exception) {
            reaction.markFailed();
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            if (reaction.getAttemptCount() >= maxAttempts) {
                reaction.markFailed();
            }
        }
    }

    public void reactTo(BidPlacedEvent event) {
        List<ProxyBidder> proxyBidders = repository.findAllByAuctionIdAndStatus(
                event.auctionId(),
                ProxyBidderStatus.ACTIVE
        );

        for (ProxyBidder proxyBidder : proxyBidders) {
            String reactionId = proxyBidder.getId() + ":" + event.eventId();

            boolean alreadyHandled = reactionRepository.findByProxyBidderIdAndSourceEventId(
                    proxyBidder.getId(),
                    event.eventId()
            ).isPresent();

            if (alreadyHandled) {
                continue;
            }

            ProxyBidderReaction reaction = new ProxyBidderReaction(
                    reactionId,
                    proxyBidder.getId(),
                    event.eventId(),
                    event.auctionId(),
                    null
            );

            try {
                reaction = reactionRepository.saveAndFlush(reaction);
            } catch (DataIntegrityViolationException exception) {
                continue;
            }

            AuctionBidStateResponse response =
                    stateClient.getBidState(event.auctionId());

            if (response.status() != AuctionStatus.OPEN) {
                reaction.markSkipped();
                reaction = reactionRepository.saveAndFlush(reaction);
                logReaction(reaction);
                continue;
            }

            boolean ownerAlreadyLeads = Objects.equals(
                    response.highestBidderId(),
                    proxyBidder.getBidderId()
            );

            if (ownerAlreadyLeads) {
                reaction.markSkipped();
                reaction = reactionRepository.saveAndFlush(reaction);
                logReaction(reaction);
                continue;
            }

            BigDecimal proposedAmount =
                    response.currentPrice()
                            .add(response.minIncrement());

            reaction.recordProposal(proposedAmount);

            if (proposedAmount.compareTo(
                    proxyBidder.getBudget()) > 0) {
                reaction.markSkipped();
                reaction = reactionRepository.saveAndFlush(reaction);
                logReaction(reaction);
                continue;
            }

            reaction.recordAttempt();
            reaction.backOff(retryBaseDelay);
            reaction = reactionRepository.saveAndFlush(reaction);

            AgentBidCommand agentBidCommand = new AgentBidCommand(
                    proxyBidder.getBidderId(),
                    proposedAmount,
                    reactionId
            );

            recordCommandOutcome(reaction, () -> commandClient.placeBid(
                    event.auctionId(),
                    agentBidCommand
            ));

            reaction = reactionRepository.saveAndFlush(reaction);
            logReaction(reaction);
        }
    }


    public void retryPending(String reactionId) {
        ProxyBidderReaction reaction = reactionRepository
                .findById(reactionId)
                .orElse(null);

        if (reaction == null ||
                reaction.getOutcome() !=
                        ProxyBidderReactionOutcome.PENDING) {
            return;
        }

        if (reaction.getAttemptCount() >= maxAttempts) {
            reaction.markFailed();
            reaction = reactionRepository.saveAndFlush(reaction);
            logReaction(reaction);
            return;
        }

        ProxyBidder proxyBidder = repository
                .findById(reaction.getProxyBidderId())
                .orElse(null);

        if (proxyBidder == null) {
            reaction.markFailed();
            reaction = reactionRepository.saveAndFlush(reaction);
            logReaction(reaction);
            return;
        }

        if (proxyBidder.getStatus() != ProxyBidderStatus.ACTIVE) {
            reaction.markSkipped();
            reaction = reactionRepository.saveAndFlush(reaction);
            logReaction(reaction);
            return;
        }

        reaction.recordAttempt();
        reaction.backOff(retryBaseDelay);
        reactionRepository.saveAndFlush(reaction);

        try {
            BigDecimal proposedAmount = reaction.getProposedAmount();

            if (proposedAmount == null) {
                AuctionBidStateResponse auctionBidStateResponse = stateClient.getBidState(reaction.getAuctionId());

                if (auctionBidStateResponse.status() != AuctionStatus.OPEN || Objects.equals(auctionBidStateResponse.highestBidderId(), proxyBidder.getBidderId())) {
                    reaction.markSkipped();
                    reaction = reactionRepository.saveAndFlush(reaction);
                    logReaction(reaction);
                    return;
                }

                proposedAmount = auctionBidStateResponse.currentPrice()
                        .add(auctionBidStateResponse.minIncrement());
                reaction.recordProposal(proposedAmount);
            }

            if (proposedAmount.compareTo(proxyBidder.getBudget()) > 0) {
                reaction.markSkipped();
                reaction = reactionRepository.saveAndFlush(reaction);
                logReaction(reaction);
                return;
            }

            AgentBidCommand command = new AgentBidCommand(
                    proxyBidder.getBidderId(),
                    proposedAmount,
                    reaction.getReactionId()
            );

            String targetAuctionId = reaction.getAuctionId();
            recordCommandOutcome(reaction, () -> commandClient.placeBid(
                    targetAuctionId,
                    command
            ));
        } catch (HttpClientErrorException exception) {
            reaction.markFailed();
        } catch (HttpServerErrorException |
                ResourceAccessException exception) {
            if (reaction.getAttemptCount() >= maxAttempts) {
                reaction.markFailed();
            }
        }
        reactionRepository.saveAndFlush(reaction);
        logReaction(reaction);
    }


    private void logReaction(ProxyBidderReaction reaction) {
        log.info(
                "Proxy Bidder reaction: reactionId={}, sourceEventId={}, auctionId={}, proposedAmount={}, attempt={}, outcome={}",
                reaction.getReactionId(),
                reaction.getSourceEventId(),
                reaction.getAuctionId(),
                reaction.getProposedAmount(),
                reaction.getAttemptCount(),
                reaction.getOutcome()
        );
    }
}
