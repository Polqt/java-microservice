package com.auction.auctionservice.service;

import com.auction.auctionservice.dto.*;
import com.auction.auctionservice.exception.*;
import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.model.Bid;
import com.auction.auctionservice.model.Deal;
import com.auction.auctionservice.repository.AuctionRepository;
import com.auction.auctionservice.repository.BidRepository;
import com.auction.auctionservice.repository.DealRepository;
import com.auction.auctionservice.event.AuctionClosedEvent;
import com.auction.auctionservice.event.DealCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.auction.auctionservice.event.BidPlacedEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuctionService {

    /** Server-side ceiling — a client's requested page size can widen, never past this. */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Safety cap on how many candidate rows browseAuctions loads before filtering by
     * effective status in Java. ponytail: this repo has no other query needing more
     * than that, and no scheduler to keep a derived-status column in sync — a raw SQL
     * equality on `status` can't express "OPEN or SCHEDULED-but-already-started", so
     * filtering in Java is the honest choice here, bounded so it can't ever load the
     * whole table unbounded. Revisit if this becomes a real production dataset size.
     */
    private static final int MAX_BROWSE_CANDIDATES = 1000;

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DealRepository dealRepository;

    @Transactional(readOnly = true)
    public AuctionResponse getAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
        return toAuctionResponse(auction);
    }

    /** Public browse. Defaults to OPEN when no status is given (spec: browsing hides finished work by default). */
    @Transactional(readOnly = true)
    public AuctionPageResponse browseAuctions(AuctionStatus status, int page, int size) {
        AuctionStatus requestedStatus = status != null ? status : AuctionStatus.OPEN;
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int boundedPage = Math.max(page, 0);
        LocalDateTime now = LocalDateTime.now();

        // A raw equality on the stored column can't express "OPEN or SCHEDULED whose
        // window already opened" — the same case that made bidding disagree with
        // reads before. Widen to every raw status that could resolve to the one
        // asked for, then filter by the same effectiveStatus placeBid gates on, so
        // browsing can never disagree with what a Bid on the same row would do.
        List<Auction> candidates = auctionRepository.findByStatusIn(
                candidateRawStatusesFor(requestedStatus),
                PageRequest.of(0, MAX_BROWSE_CANDIDATES)
        );

        List<Auction> matching = candidates.stream()
                .filter(auction -> effectiveStatus(auction, now) == requestedStatus)
                .toList();

        int fromIndex = Math.min(boundedPage * boundedSize, matching.size());
        int toIndex = Math.min(fromIndex + boundedSize, matching.size());
        int totalPages = (int) Math.ceil((double) matching.size() / boundedSize);

        return new AuctionPageResponse(
                matching.subList(fromIndex, toIndex).stream().map(this::toAuctionResponse).toList(),
                boundedPage,
                boundedSize,
                matching.size(),
                totalPages
        );
    }

    @Transactional
    public AuctionResponse createAuction(String sellerId, CreateAuctionRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new InvalidAuctionWindowException("endAt must be after startAt");
        }

        LocalDateTime now = LocalDateTime.now();
        if (request.endAt().isBefore(now)) {
            throw new InvalidAuctionWindowException("endAt is already in the past");
        }

        Auction auction = new Auction();
        auction.setSellerId(sellerId);
        auction.setTitle(request.title());
        auction.setStartingPrice(request.startingPrice());
        auction.setCurrentPrice(request.startingPrice());
        auction.setMinIncrement(request.minIncrement());
        auction.setStatus(request.startAt().isAfter(now) ? AuctionStatus.SCHEDULED : AuctionStatus.OPEN);
        auction.setStartAt(request.startAt());
        auction.setEndAt(request.endAt());

        return toAuctionResponse(auctionRepository.saveAndFlush(auction));
    }

    /** Human bid path, no idempotency key; the caller is a person clicking once. */
    @Transactional
    public BidResponse placeBid(String auctionId, String bidderId, BigDecimal amount) {
        return placeBid(auctionId, bidderId, amount, null);
    }

    /**
     * Bid path with an optional idempotency key (reactionId).
     * Agents retry, on redelivery, restart, or a network blip, so the same logical
     * reaction must never place two Bids. A replayed reactionId returns the original Bid.
     */
    @Transactional
    public BidResponse placeBid(String auctionId, String bidderId, BigDecimal amount, String reactionId) {
        if (reactionId != null) {
            Optional<Bid> alreadyPlaced = bidRepository.findByReactionId(reactionId);
            if (alreadyPlaced.isPresent()) {
                return toBidResponse(alreadyPlaced.get());
            }
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (bidderId.equals(auction.getSellerId())) {
            throw new SellerCannotBidException(auctionId);
        }

        LocalDateTime now = LocalDateTime.now();

        // Single shared derivation with the read path (effectiveStatus) — these two
        // independently re-implementing "is this Auction open" is exactly what let
        // them drift apart before: this check didn't look past endAt, so a read could
        // say OPEN for an Auction that had already stopped accepting Bids.
        if (effectiveStatus(auction, now) != AuctionStatus.OPEN) {
            throw new AuctionNotOpenException(auctionId);
        }

        if (auction.getHighestBidderId() == null) {
            if (amount.compareTo(auction.getStartingPrice()) < 0) {
                throw new BidBelowStartingPriceException(auctionId);
            }
        } else {
            BigDecimal minimumBid =
                    auction.getCurrentPrice().add(auction.getMinIncrement());

            if (amount.compareTo(minimumBid) < 0) {
                throw new BidTooLowException(auctionId);
            }
        }

        auction.setCurrentPrice(amount);
        auction.setHighestBidderId(bidderId);

        auctionRepository.saveAndFlush(auction);

        Bid bid = new Bid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setReactionId(reactionId);

        Bid savedBid = bidRepository.saveAndFlush(bid);

        eventPublisher.publishEvent(new BidPlacedEvent(
                UUID.randomUUID(),
                auctionId,
                savedBid.getId(),
                bidderId,
                savedBid.getAmount(),
                savedBid.getPlacedAt()
        ));

        BidResponse response = new BidResponse();
        response.setBidId(savedBid.getId());
        response.setAuctionId(savedBid.getAuctionId());
        response.setAmount(savedBid.getAmount());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setLeading(true);
        response.setPlacedAt(savedBid.getPlacedAt());

        return response;
    }
    
    @Transactional
    public CloseAuctionResponse closeAuction(String auctionId, String callerId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        // Only the owning Seller may close. Checked here, not in the controller,
        // this is the only place holding the persisted auction.
        if (!callerId.equals(auction.getSellerId())) {
            throw new NotAuctionOwnerException(auctionId);
        }

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            Deal existingDeal = dealRepository.findByAuctionId(auctionId)
                    .orElse(null);

            return toCloseAuctionResponse(auction, existingDeal);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(auction.getEndAt())) {
            throw new AuctionCloseTooEarlyException(
                    auctionId + ", closes at " + auction.getEndAt()
            );
        }

        Optional<Bid> winningBid = bidRepository.findTopByAuctionIdOrderByAmountDescPlacedAtAsc(auctionId);


        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(now);
        auctionRepository.saveAndFlush(auction);

        Deal deal = null;

        if (winningBid.isPresent()) {
            Bid bid = winningBid.get();

            deal = new Deal();
            deal.setAuctionId(auctionId);
            deal.setSellerId(auction.getSellerId());
            deal.setWinningBidderId(bid.getBidderId());
            deal.setWinningBidId(bid.getId());
            deal.setFinalPrice(auction.getCurrentPrice());

            deal = dealRepository.saveAndFlush(deal);
        }

        eventPublisher.publishEvent(new AuctionClosedEvent(
                UUID.randomUUID(),
                auction.getId(),
                auction.getClosedAt()
        ));

        if (deal != null) {
            eventPublisher.publishEvent(new DealCreatedEvent(
                    UUID.randomUUID(),
                    deal.getId(),
                    deal.getAuctionId(),
                    deal.getSellerId(),
                    deal.getWinningBidderId(),
                    deal.getWinningBidId(),
                    deal.getFinalPrice(),
                    deal.getCreatedAt()
            ));
        }

        return toCloseAuctionResponse(auction, deal);
    }

    /**
     * Minimal Deal read, gated to the two parties. Not-found (never forbidden) for
     * anyone else — same rule as closeAuction's ownership check, so a losing Bidder
     * or unrelated Seller cannot even confirm the Deal exists.
     */
    @Transactional(readOnly = true)
    public DealResponse getDeal(String dealId, String callerId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new DealNotFoundException(dealId));

        boolean isParty = callerId.equals(deal.getSellerId())
                || callerId.equals(deal.getWinningBidderId());

        if (!isParty) {
            throw new DealNotFoundException(dealId);
        }

        return new DealResponse(
                deal.getId(),
                deal.getAuctionId(),
                deal.getSellerId(),
                deal.getWinningBidderId(),
                deal.getWinningBidId(),
                deal.getFinalPrice(),
                deal.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AuctionBidStateResponse getBidState(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        return new AuctionBidStateResponse(
                auction.getId(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.getMinIncrement(),
                auction.getStatus(),
                auction.getStartAt(),
                auction.getEndAt(),
                auction.getHighestBidderId()
        );
    }

    /** Replay of an already-placed Bid: report it as-is rather than bidding again. */
    private BidResponse toBidResponse(Bid bid) {
        Auction auction = auctionRepository.findById(bid.getAuctionId())
                .orElseThrow(() -> new AuctionNotFoundException(bid.getAuctionId()));

        BidResponse response = new BidResponse();
        response.setBidId(bid.getId());
        response.setAuctionId(bid.getAuctionId());
        response.setAmount(bid.getAmount());
        response.setPlacedAt(bid.getPlacedAt());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setLeading(bid.getBidderId().equals(auction.getHighestBidderId()));
        return response;
    }

    private AuctionResponse toAuctionResponse(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getSellerId(),
                auction.getTitle(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.getMinIncrement(),
                effectiveStatus(auction, LocalDateTime.now()),
                auction.getStartAt(),
                auction.getEndAt()
        );
    }

    /**
     * The single source of truth for "is this Auction open right now" — used by both
     * placeBid's gate and every read, so they cannot independently drift apart again.
     * SCHEDULED before startAt; OPEN once the window has opened (regardless of the
     * stored column); CLOSED once manually Closed, and also once endAt has passed
     * without a manual Close — bidding is already refused at that point, so reporting
     * anything else would be exactly the disagreement this method exists to prevent.
     */
    private AuctionStatus effectiveStatus(Auction auction, LocalDateTime now) {
        if (auction.getStatus() == AuctionStatus.CLOSED) {
            return AuctionStatus.CLOSED;
        }
        if (now.isBefore(auction.getStartAt())) {
            return AuctionStatus.SCHEDULED;
        }
        if (!now.isBefore(auction.getEndAt())) {
            return AuctionStatus.CLOSED;
        }
        return AuctionStatus.OPEN;
    }

    /**
     * Which raw stored statuses could ever resolve to the given effective status —
     * see effectiveStatus(). Only OPEN needs widening beyond its own literal value:
     * a SCHEDULED row whose window has opened, or an OPEN row whose window has
     * ended, are the two cases a plain equality query on the stored column misses.
     */
    private static Set<AuctionStatus> candidateRawStatusesFor(AuctionStatus requestedEffectiveStatus) {
        return switch (requestedEffectiveStatus) {
            case OPEN -> EnumSet.of(AuctionStatus.SCHEDULED, AuctionStatus.OPEN);
            case SCHEDULED -> EnumSet.of(AuctionStatus.SCHEDULED);
            case CLOSED -> EnumSet.allOf(AuctionStatus.class);
        };
    }

    private CloseAuctionResponse toCloseAuctionResponse(Auction auction, Deal deal) {
        return new CloseAuctionResponse(
                auction.getId(),
                auction.getStatus(),
                auction.getClosedAt(),
                deal == null ? null : deal.getId(),
                deal == null ? null : deal.getWinningBidderId(),
                deal == null ? null : deal.getFinalPrice()
        );
    }
}
