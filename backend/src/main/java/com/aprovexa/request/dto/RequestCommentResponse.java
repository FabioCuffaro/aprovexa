package com.aprovexa.request.dto;

import java.time.Instant;

public record RequestCommentResponse(
        Long id,
        Long requestId,
        String author,
        String content,
        Instant createdAt
) {
}
