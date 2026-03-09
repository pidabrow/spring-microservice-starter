package com.pidabrow.starter.sample.api.controller;

import com.pidabrow.starter.sample.api.dto.CreateUserRequest;
import com.pidabrow.starter.sample.api.dto.RegisterUserRequest;
import com.pidabrow.starter.sample.api.dto.UpdateUserRequest;
import com.pidabrow.starter.sample.api.dto.UserPreferencesDto;
import com.pidabrow.starter.sample.api.dto.UserResponse;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.application.usecase.DeleteUserUseCase;
import com.pidabrow.starter.sample.application.usecase.FindUsersUseCase;
import com.pidabrow.starter.sample.application.usecase.RegisterUserUseCase;
import com.pidabrow.starter.sample.application.usecase.UpdateUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user operations.
 * This is an inbound adapter following hexagonal architecture.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final RegisterUserUseCase registerUserUseCase;
    private final FindUsersUseCase findUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public UserController(
            CreateUserUseCase createUserUseCase,
            RegisterUserUseCase registerUserUseCase,
            FindUsersUseCase findUsersUseCase,
            UpdateUserUseCase updateUserUseCase,
            DeleteUserUseCase deleteUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.registerUserUseCase = registerUserUseCase;
        this.findUsersUseCase = findUsersUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
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

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        User user = registerUserUseCase.execute(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName()
        );

        UserResponse response = toResponse(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = findUsersUseCase.findAll().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        User user = findUsersUseCase.findById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserPreferences preferences = request.preferences() != null
                ? new UserPreferences(request.preferences().emailEnabled(), request.preferences().smsEnabled())
                : null;

        User user = updateUserUseCase.execute(
                id,
                request.email(),
                request.phoneNumber(),
                request.firstName(),
                request.lastName(),
                preferences
        );

        return ResponseEntity.ok(toResponse(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        deleteUserUseCase.execute(id);
        return ResponseEntity.noContent().build();
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
