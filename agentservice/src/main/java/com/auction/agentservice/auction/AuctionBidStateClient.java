package com.auction.agentservice.auction;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuctionBidStateClient {

    private final RestClient restClient;

    public AuctionBidStateClient(@Qualifier("auctionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AuctionBidStateResponse getBidState(String auctionId) {
        return restClient.get()
                .uri(
                        "/internal/auctions/{auctionId}/bid-state",
                        auctionId
                )
                .attributes(
                        RequestAttributeClientRegistrationIdResolver
                                .clientRegistrationId("auctionservice")
                )
                .attributes(
                        RequestAttributePrincipalResolver
                                .principal("agentservice")
                )
                .retrieve()
                .body(AuctionBidStateResponse.class);
    }
}
