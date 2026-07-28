package com.auction.userservice.controller;

import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.UpdateProfileRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class UserController {

    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getUserProfile(jwt.getSubject()));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, @AuthenticationPrincipal Jwt jwt) {
        UserResponse response = userService.register(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> editProfile(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        UserResponse response = userService.editProfile(
                jwt.getSubject(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteProfile(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
