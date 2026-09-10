package com.aprovexa.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
}
