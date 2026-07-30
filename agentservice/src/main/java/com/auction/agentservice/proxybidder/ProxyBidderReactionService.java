package com.auction.agentservice.proxybidder;

import com.auction.agentservice.auction.*;
import com.auction.agentservice.event.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
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

    private static final int MAX_ATTEMPTS = 3;

    public ProxyBidderReactionService(
            ProxyBidderRepository repository,
            AuctionBidStateClient stateClient,
            AuctionBidCommandClient commandClient,
            ProxyBidderReactionRepository reactionRepository
    ) {
        this.repository = repository;
        this.stateClient = stateClient;
        this.commandClient = commandClient;
        this.reactionRepository = reactionRepository;
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
                reactionRepository.saveAndFlush(reaction);
            } catch (DataIntegrityViolationException exception) {
                continue;
            }

            AuctionBidStateResponse response =
                    stateClient.getBidState(event.auctionId());

            if (response.status() != AuctionStatus.OPEN) {
                reaction.markSkipped();
                reactionRepository.saveAndFlush(reaction);
                continue;
            }

            boolean ownerAlreadyLeads = Objects.equals(
                    response.highestBidderId(),
                    proxyBidder.getBidderId()
            );

            if (ownerAlreadyLeads) {
                reaction.markSkipped();
                reactionRepository.saveAndFlush(reaction);
                continue;
            }

            BigDecimal proposedAmount =
                    response.currentPrice()
                            .add(response.minIncrement());

            reaction.recordProposal(proposedAmount);

            if (proposedAmount.compareTo(
                    proxyBidder.getBudget()) > 0) {
                reaction.markSkipped();
                reactionRepository.saveAndFlush(reaction);
                continue;
            }

            reaction.recordAttempt();
            reactionRepository.saveAndFlush(reaction);

            AgentBidCommand agentBidCommand = new AgentBidCommand(
                    proxyBidder.getBidderId(),
                    proposedAmount,
                    reactionId
            );

            try {
                commandClient.placeBid(
                        event.auctionId(),
                        agentBidCommand
                );

                reaction.markSucceeded();
            } catch (HttpClientErrorException.Conflict exception) {
                reaction.markBalked();
            }

            reactionRepository.saveAndFlush(reaction);
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

        if (reaction.getAttemptCount() >= MAX_ATTEMPTS) {
            reaction.markFailed();
            reactionRepository.saveAndFlush(reaction);
            return;
        }

        ProxyBidder proxyBidder = repository
                .findById(reaction.getProxyBidderId())
                .orElse(null);

        if (proxyBidder == null) {
            reaction.markFailed();
            reactionRepository.saveAndFlush(reaction);
            return;
        }

        if (proxyBidder.getStatus() != ProxyBidderStatus.ACTIVE) {
            reaction.markSkipped();
            reactionRepository.saveAndFlush(reaction);
            return;
        }

        reaction.recordAttempt();

        try {
            BigDecimal proposedAmount = reaction.getProposedAmount();

            if (proposedAmount == null) {
                AuctionBidStateResponse auctionBidStateResponse = stateClient.getBidState(reaction.getAuctionId());

                if (auctionBidStateResponse.status() != AuctionStatus.OPEN || Objects.equals(auctionBidStateResponse.highestBidderId(), proxyBidder.getBidderId())) {
                    reaction.markSkipped();
                    reactionRepository.saveAndFlush(reaction);
                    return;
                }

                proposedAmount = auctionBidStateResponse.currentPrice()
                        .add(auctionBidStateResponse.minIncrement());
                reaction.recordProposal(proposedAmount);
            }

            if (proposedAmount.compareTo(proxyBidder.getBudget()) > 0) {
                reaction.markSkipped();
                reactionRepository.saveAndFlush(reaction);
                return;
            }

            AgentBidCommand command = new AgentBidCommand(
                    proxyBidder.getBidderId(),
                    proposedAmount,
                    reaction.getReactionId()
            );

            commandClient.placeBid(
                    reaction.getAuctionId(),
                    command
            );

            reaction.markSucceeded();
        } catch (HttpClientErrorException.Conflict exception) {
            reaction.markBalked();
        } catch (HttpClientErrorException exception) {
            reaction.markFailed();
        } catch (HttpServerErrorException |
                ResourceAccessException exception) {
            if (reaction.getAttemptCount() >= MAX_ATTEMPTS) {
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
