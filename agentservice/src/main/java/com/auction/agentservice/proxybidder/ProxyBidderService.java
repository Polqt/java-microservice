package com.auction.agentservice.proxybidder;

import com.auction.agentservice.auction.AuctionBidStateClient;
import com.auction.agentservice.auction.AuctionBidStateResponse;
import com.auction.agentservice.auction.AuctionStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProxyBidderService {

    private final ProxyBidderRepository repository;
    private final AuctionBidStateClient auctionBidStateClient;

    public ProxyBidderService(ProxyBidderRepository repository, AuctionBidStateClient auctionBidStateClient) {
        this.repository = repository;
        this.auctionBidStateClient = auctionBidStateClient;
    }

    public ProxyBidderResponse create(String bidderId, CreateProxyBidderRequest request) {
        boolean alreadyExists = repository.existsByAuctionIdAndBidderId(request.auctionId(), bidderId);

        if (alreadyExists) {
            throw new ProxyBidderAlreadyExistsException(bidderId);
        }

        AuctionBidStateResponse auctionBidStateResponse = auctionBidStateClient.getBidState(request.auctionId());

        if (request.budget()
                .compareTo(auctionBidStateResponse.startingPrice()) < 0) {
            throw new BudgetBelowStartingPriceException(bidderId);
        }

        ProxyBidder proxyBidder = new ProxyBidder(
                request.auctionId(),
                bidderId,
                request.budget()
        );

        try {
            ProxyBidder savedProxyBidder = repository.saveAndFlush(proxyBidder);
            return toResponse(savedProxyBidder);
        } catch (DataIntegrityViolationException e) {
            throw new ProxyBidderAlreadyExistsException(bidderId);
        }
    }

    @Transactional
    public ProxyBidderResponse updateBudget(String id, String bidderId, UpdateProxyBidderBudgetRequest request) {
        ProxyBidder proxyBidder = repository.findByIdAndBidderId(id, bidderId).orElseThrow(() -> new ProxyBidderNotFoundException(id));

        if (proxyBidder.getStatus() == ProxyBidderStatus.COMPLETED) {
            throw new ProxyBidderCompletedException(id);
        }

        AuctionBidStateResponse auctionBidStateResponse = auctionBidStateClient.getBidState(proxyBidder.getAuctionId());

        if (request.budget().compareTo(auctionBidStateResponse.startingPrice()) < 0) {
            throw new BudgetBelowStartingPriceException(bidderId);
        }

        proxyBidder.setBudget(request.budget());

        return toResponse(repository.saveAndFlush(proxyBidder));
    }

    public ProxyBidderResponse get(String id, String bidderId) {
        ProxyBidder proxyBidder = repository.findByIdAndBidderId(id, bidderId)
                .orElseThrow(()-> new ProxyBidderNotFoundException(id));

        return toResponse(proxyBidder);
    }

    @Transactional
    public ProxyBidderResponse pause(String id, String bidderId) {
        ProxyBidder proxyBidder = repository.findByIdAndBidderId(id, bidderId)
                .orElseThrow(() -> new ProxyBidderNotFoundException(id));

        if (proxyBidder.getStatus() == ProxyBidderStatus.COMPLETED) {
            throw new ProxyBidderCompletedException(id);
        }

        proxyBidder.setStatus(ProxyBidderStatus.PAUSED);

        return toResponse(repository.saveAndFlush(proxyBidder));
    }

    @Transactional
    public ProxyBidderResponse reactivate(String id, String bidderId) {
        ProxyBidder proxyBidder = repository.findByIdAndBidderId(id, bidderId)
                .orElseThrow(() -> new ProxyBidderNotFoundException(id));

        if (proxyBidder.getStatus() == ProxyBidderStatus.COMPLETED) {
            throw new ProxyBidderCompletedException(id);
        }

        AuctionBidStateResponse auctionBidStateResponse = auctionBidStateClient.getBidState(proxyBidder.getAuctionId());

        if (auctionBidStateResponse.status() != AuctionStatus.OPEN) {
            throw new AuctionNotOpenException(proxyBidder.getAuctionId());
        }

        proxyBidder.setStatus(ProxyBidderStatus.ACTIVE);

        return toResponse(repository.saveAndFlush(proxyBidder));


    }

    private ProxyBidderResponse toResponse(ProxyBidder proxyBidder) {
        return new ProxyBidderResponse(
                proxyBidder.getId(),
                proxyBidder.getAuctionId(),
                proxyBidder.getBudget(),
                proxyBidder.getStatus(),
                proxyBidder.getCreatedAt(),
                proxyBidder.getUpdatedAt()
        );
    }


}
