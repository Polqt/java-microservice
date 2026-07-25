package com.auction.auctionservice.service;

import com.auction.auctionservice.dto.BidResponse;
import com.auction.auctionservice.exception.*;
import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.model.Bid;
import com.auction.auctionservice.repository.AuctionRepository;
import com.auction.auctionservice.repository.BidRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    
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

        BidResponse response = new BidResponse();
        response.setBidId(savedBid.getId());
        response.setAuctionId(savedBid.getAuctionId());
        response.setAmount(savedBid.getAmount());
        response.setCurrentPrice(auction.getCurrentPrice());
        response.setLeading(true);
        response.setPlacedAt(savedBid.getPlacedAt());

        return response;
    }
}
