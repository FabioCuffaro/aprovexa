package com.aprovexa.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransitionRequest(
        @NotBlank
        @Size(max = 120)
        @Schema(example = "Laura")
        String actor
) {
}
