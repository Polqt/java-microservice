package com.auction.auctionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Browse and read are public — a Bidder must be able to look before signing in.
     * "*" is one path segment: it does not reach /{id}/bids (two segments after
     * /api/auctions), but it DOES match /mine (one segment) — so /mine's own rule
     * must be declared first. Spring Security's authorizeHttpRequests picks the
     * first matching rule, not the most specific one, so declaration order here is
     * load-bearing: reordering these two would silently make /mine public.
     *
     * Duplicated verbatim in gatewayservice's own SecurityConfig, which validates
     * the same JWT before this service ever sees the request — these are two
     * independently deployed services with no shared module for one predicate, so
     * this is a deliberate, acknowledged duplication, not an oversight. If either
     * changes, update both.
     */
    private static final String[] PUBLIC_AUCTION_READ_PATHS = {"/api/auctions", "/api/auctions/*"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Specific role-gated rules before the general public-read wildcard — see the comment above.
                        .requestMatchers(HttpMethod.GET, "/api/auctions/mine").hasRole("SELLER")
                        .requestMatchers(HttpMethod.GET, "/api/bids/mine").hasRole("BIDDER")
                        .requestMatchers(HttpMethod.GET, "/api/auctions/*/bids").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_AUCTION_READ_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auctions/*/bids")
                        .hasRole("BIDDER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                )
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess =
                    jwt.getClaim("realm_access");

            if (realmAccess == null ||
                    !(realmAccess.get("roles")
                            instanceof Collection<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .<GrantedAuthority>map(role ->
                            new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });

        return converter;
    }
}