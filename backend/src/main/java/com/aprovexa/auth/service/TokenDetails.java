package com.aprovexa.auth.service;

import java.time.Instant;

public record TokenDetails(String token, Instant expiresAt) {
}
