package com.aprovexa.request.dto;

import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;

import java.time.Instant;

public record RequestResponse(
        Long id,
        RequestType type,
        RequestStatus status,
        String title,
        String description,
        String justification,
        String requester,
        Instant createdAt,
        Instant updatedAt
) {
}
