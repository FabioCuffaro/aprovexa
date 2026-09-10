package com.aprovexa.security;

import com.aprovexa.auth.model.Permission;
import com.aprovexa.auth.model.Role;

import java.util.Set;

public record CurrentUser(
        String email,
        Role role,
        Set<Permission> permissions
) {
    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
