package com.aprovexa.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        boolean invalidToken = exception instanceof InvalidBearerTokenException;
        errorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                invalidToken ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED",
                invalidToken ? "The bearer token is invalid or expired" : "Authentication is required"
        );
    }
}
