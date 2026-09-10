package com.aprovexa.auth.dto;

import com.aprovexa.auth.model.Permission;
import com.aprovexa.auth.model.Role;

import java.time.Instant;
import java.util.Set;

public record UserProfileResponse(
        Long id,
        String displayName,
        String email,
        Role role,
        Set<Permission> permissions,
        Instant createdAt
) {
}
