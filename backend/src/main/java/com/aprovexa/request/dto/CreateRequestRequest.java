package com.aprovexa.request.dto;

import com.aprovexa.request.model.RequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRequestRequest(
        @NotNull
        @Schema(example = "PURCHASE")
        RequestType type,

        @NotBlank
        @Size(min = 3, max = 120)
        @Schema(example = "Laptop for development work")
        String title,

        @NotBlank
        @Size(min = 10, max = 2000)
        @Schema(example = "A laptop is required to work on the new backend project.")
        String description,

        @Size(max = 1000)
        @Schema(example = "The current device cannot run the required development environment.")
        String justification,

        @NotBlank
        @Size(max = 120)
        @Schema(example = "Laura")
        String requester
) {
}
