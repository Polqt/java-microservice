package com.auction.userservice.service;

import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.UpdateProfileRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.exception.EmailAlreadyExistsException;
import com.auction.userservice.exception.UserAlreadyExistsException;
import com.auction.userservice.exception.UserNotFoundException;
import com.auction.userservice.model.User;
import com.auction.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    @Transactional
    public UserResponse register(String userId, String email, RegisterRequest request) {
        if (repository.existsById(userId)) {
            throw new UserAlreadyExistsException(userId);
        }

         if (repository.existsByEmail(email)) {
             throw new EmailAlreadyExistsException(email);
         }

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User savedUser = repository.save(user);
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toResponse(user);
    }

    // Read-then-write must be one transaction, or a concurrent update can be lost.
    @Transactional
    public UserResponse editProfile(String userId, UpdateProfileRequest request) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        User savedUser = repository.saveAndFlush(user);

        return toResponse(savedUser);
    }

    @Transactional
    public void deleteProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        repository.delete(user);
    }

    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedDate(user.getCreatedDate());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
