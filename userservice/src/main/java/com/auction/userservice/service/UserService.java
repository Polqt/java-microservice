package com.auction.userservice.service;

import com.auction.userservice.dto.RegisterRequest;
import com.auction.userservice.dto.UserResponse;
import com.auction.userservice.exception.EmailAlreadyExistsException;
import com.auction.userservice.exception.UserNotFoundException;
import com.auction.userservice.model.User;
import com.auction.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    public UserResponse register(RegisterRequest request) {

         if (repository.existsByEmail(request.getEmail())) {
             throw new EmailAlreadyExistsException(request.getEmail());
         }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        User savedUser = repository.save(user);
        UserResponse response = new UserResponse();

        response.setId(savedUser.getId());
        response.setEmail(savedUser.getEmail());
        response.setFirstName(savedUser.getFirstName());
        response.setLastName(savedUser.getLastName());
        response.setCreatedDate(savedUser.getCreatedDate());
        response.setUpdatedAt(savedUser.getUpdatedAt());
        return response;
    }

    public UserResponse getUserProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedDate(user.getCreatedDate());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    public void deleteProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        repository.delete(user);
    }
}
