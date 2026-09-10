package com.aprovexa.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 120)
        @Schema(example = "laura@example.com")
        String email,

        @NotBlank
        @Size(max = 72)
        @Schema(example = "ChangeMe123!", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password
) {
}
