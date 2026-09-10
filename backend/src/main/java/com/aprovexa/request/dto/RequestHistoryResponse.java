package com.aprovexa.request.dto;

import com.aprovexa.request.model.RequestStatus;

import java.time.Instant;

public record RequestHistoryResponse(
        Long id,
        Long requestId,
        RequestStatus previousStatus,
        RequestStatus newStatus,
        String changedBy,
        Instant changedAt
) {
}
