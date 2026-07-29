package com.auction.agentservice.proxybidder;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proxy-bidders")
public class ProxyBidderController {

    private final ProxyBidderService service;

    public ProxyBidderController(ProxyBidderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProxyBidderResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProxyBidderRequest request) {
        ProxyBidderResponse response = service.create(jwt.getSubject(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ProxyBidderResponse get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, jwt.getSubject());
    }

    @PatchMapping
    public ResponseEntity<ProxyBidderResponse> update(@AuthenticationPrincipal Jwt jwt,  @Valid @RequestBody UpdateProxyBidderBudgetRequest request) {
        ProxyBidderResponse response = service.update(jwt.getSubject(), request);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
