package com.auction.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Mirrors auctionservice's own rule — the gateway must not demand a token for
     * something auctionservice itself serves publicly. "*" is one path segment: it
     * does not reach /{id}/bids, but it DOES match /mine — so /mine's own rule must
     * be declared first (authorizeHttpRequests picks the first matching rule, not
     * the most specific one). The gateway only distinguishes public vs authenticated;
     * it has no realm-role-aware JWT converter (unlike auctionservice's own), so
     * role-specific enforcement (SELLER vs BIDDER) is left to auctionservice.
     *
     * Duplicated verbatim in auctionservice's own SecurityConfig. These are two
     * independently deployed services with no shared module for one predicate, so
     * this is a deliberate, acknowledged duplication, not an oversight. If either
     * changes, update both.
     */
    private static final String[] PUBLIC_AUCTION_READ_PATHS = {"/api/auctions", "/api/auctions/*"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auctions/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/bids/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auctions/*/bids").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_AUCTION_READ_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                )
                .build();
    }
}