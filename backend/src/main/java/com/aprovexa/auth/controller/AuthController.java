package com.aprovexa.auth.controller;

import com.aprovexa.auth.dto.AuthResponse;
import com.aprovexa.auth.dto.LoginRequest;
import com.aprovexa.auth.dto.RegisterRequest;
import com.aprovexa.auth.dto.UserProfileResponse;
import com.aprovexa.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login and authenticated profile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a USER account")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest input) {
        UserProfileResponse created = authService.register(input);
        return ResponseEntity
                .created(URI.create("/api/v1/auth/me"))
                .body(created);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email/password and obtain a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest input) {
        return authService.login(input);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user profile")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfileResponse profile(Authentication authentication) {
        return authService.profile(authentication.getName());
    }
}
