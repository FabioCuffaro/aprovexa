package com.aprovexa.security;

import com.aprovexa.auth.model.Role;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public CurrentUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication is required");
        }

        Role role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> Role.valueOf(authority.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new InsufficientAuthenticationException("Authenticated user has no role"));

        return new CurrentUser(authentication.getName(), role, role.permissions());
    }
}
