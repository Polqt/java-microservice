package com.auction.userservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100)
    @Pattern(
            regexp = ".*\\S.*",
            message = "Name must not be blank"
    )
    private String firstName;

    @Size(max = 100)
    @Pattern(
            regexp = ".*\\S.*",
            message = "Name must not be blank"
    )
    private String lastName;
}
