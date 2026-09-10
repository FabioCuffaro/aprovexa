package com.aprovexa.request.controller;

import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.PageResponse;
import com.aprovexa.request.dto.RequestResponse;
import com.aprovexa.request.dto.UpdateRequestRequest;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/requests")
@Validated
@Tag(name = "Requests", description = "Request management and lifecycle transitions")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @Operation(summary = "Create a request in CREATED status")
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestRequest input) {
        RequestResponse created = requestService.create(input);
        return ResponseEntity
                .created(URI.create("/api/v1/requests/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a request by id")
    public RequestResponse findById(@PathVariable Long id) {
        return requestService.findById(id);
    }

    @GetMapping
    @Operation(summary = "List requests with pagination and optional type/status filters")
    public PageResponse<RequestResponse> findAll(
            @RequestParam(required = false) RequestType type,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        return requestService.findAll(type, status, page, size, sortBy, direction);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a CREATED request")
    public RequestResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequestRequest input
    ) {
        return requestService.update(id, input);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a CREATED request")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        requestService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Move a request from CREATED to IN_REVIEW")
    public RequestResponse submit(@PathVariable Long id) {
        return requestService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a request in IN_REVIEW")
    public RequestResponse approve(@PathVariable Long id) {
        return requestService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a request in IN_REVIEW")
    public RequestResponse reject(@PathVariable Long id) {
        return requestService.reject(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an unresolved request")
    public RequestResponse cancel(@PathVariable Long id) {
        return requestService.cancel(id);
    }
}
