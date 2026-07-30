package com.auction.agentservice.auction;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.web.client
        .RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuctionBidCommandClient {

    private final RestClient restClient;

    public AuctionBidCommandClient(@Qualifier ("auctionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AgentBidResponse placeBid(String auctionId, AgentBidCommand command) {
        return restClient.post()
                .uri(
                        "/internal/auctions/{auctionId}/bids",
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
                .body(command)
                .retrieve()
                .body(AgentBidResponse.class);
    }
}
