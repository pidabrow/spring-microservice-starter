package com.pidabrow.starter.sample.api.controller;

import com.pidabrow.starter.sample.api.dto.CreateUserRequest;
import com.pidabrow.starter.sample.api.dto.UserPreferencesDto;
import com.pidabrow.starter.sample.api.dto.UserResponse;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user operations.
 * This is an inbound adapter following hexagonal architecture.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final CreateUserUseCase createUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase) {
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        // Preferences are validated by @NotNull, but we add null check for safety
        if (request.preferences() == null) {
            throw new IllegalArgumentException("Preferences are required");
        }
        UserPreferences preferences = new UserPreferences(
                request.preferences().emailEnabled(),
                request.preferences().smsEnabled()
        );

        User user = createUserUseCase.execute(
                request.email(),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                preferences
        );

        UserResponse response = toResponse(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UserResponse toResponse(User user) {
        UserPreferencesDto preferencesDto = new UserPreferencesDto(
                user.preferences().emailEnabled(),
                user.preferences().smsEnabled()
        );
        return new UserResponse(
                user.id(),
                user.tenantId(),
                user.email(),
                user.phoneNumber(),
                user.firstName(),
                user.lastName(),
                preferencesDto
        );
    }
}

