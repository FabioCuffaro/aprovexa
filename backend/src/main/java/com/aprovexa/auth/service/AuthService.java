package com.aprovexa.auth.service;

import com.aprovexa.auth.dto.AuthResponse;
import com.aprovexa.auth.dto.LoginRequest;
import com.aprovexa.auth.dto.RegisterRequest;
import com.aprovexa.auth.dto.UserProfileResponse;
import com.aprovexa.auth.model.Role;
import com.aprovexa.auth.model.UserAccount;
import com.aprovexa.auth.repository.UserAccountRepository;
import com.aprovexa.common.error.UserAlreadyExistsException;
import com.aprovexa.common.error.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest input) {
        String email = normalizeEmail(input.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        UserAccount user = new UserAccount(
                email,
                passwordEncoder.encode(input.password()),
                input.displayName().trim(),
                Role.USER
        );

        try {
            return toProfile(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new UserAlreadyExistsException(email);
        }
    }

    public AuthResponse login(LoginRequest input) {
        String email = normalizeEmail(input.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, input.password())
        );

        UserAccount user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        TokenDetails token = jwtService.issue(user);

        return new AuthResponse(
                token.token(),
                "Bearer",
                token.expiresAt(),
                toProfile(user)
        );
    }

    public UserProfileResponse profile(String email) {
        UserAccount user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException(email));
        return toProfile(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                user.getRole().permissions(),
                user.getCreatedAt()
        );
    }
}
