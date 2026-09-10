package com.aprovexa.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequestCommentRequest(
        @NotBlank
        @Size(max = 120)
        @Schema(example = "Laura")
        String author,

        @NotBlank
        @Size(max = 2000)
        @Schema(example = "Please confirm whether this purchase includes a docking station.")
        String content
) {
}
