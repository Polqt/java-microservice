package com.auction.auctionservice.service;

import com.auction.auctionservice.dto.BidResponse;
import com.auction.auctionservice.dto.CloseAuctionResponse;
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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.auction.auctionservice.event.BidPlacedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DealRepository dealRepository;

    @Transactional
    public BidResponse placeBid(String auctionId, String bidderId, BigDecimal amount) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (bidderId.equals(auction.getSellerId())) {
            throw new SellerCannotBidException(auctionId);
        }

        LocalDateTime now = LocalDateTime.now();

        boolean outsideOpenWindow =
                auction.getStatus() != AuctionStatus.OPEN
                || now.isBefore(auction.getStartAt())
                || !now.isBefore(auction.getEndAt());

        if (outsideOpenWindow) {
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
    public CloseAuctionResponse closeAuction(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

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
