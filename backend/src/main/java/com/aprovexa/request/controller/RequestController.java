package com.aprovexa.request.controller;

import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.PageResponse;
import com.aprovexa.request.dto.RequestCommentResponse;
import com.aprovexa.request.dto.RequestHistoryResponse;
import com.aprovexa.request.dto.RequestResponse;
import com.aprovexa.request.dto.TransitionRequest;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/requests")
@Validated
@Tag(name = "Requests", description = "Request management, audit history and comments")
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
    @Operation(summary = "Move a request from CREATED to IN_REVIEW and audit the actor")
    public RequestResponse submit(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest input
    ) {
        return requestService.submit(id, input);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an IN_REVIEW request and audit the actor")
    public RequestResponse approve(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest input
    ) {
        return requestService.approve(id, input);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an IN_REVIEW request and audit the actor")
    public RequestResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest input
    ) {
        return requestService.reject(id, input);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an unresolved request and audit the actor")
    public RequestResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest input
    ) {
        return requestService.cancel(id, input);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment to a request")
    public ResponseEntity<RequestCommentResponse> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CreateRequestCommentRequest input
    ) {
        RequestCommentResponse created = requestService.addComment(id, input);
        return ResponseEntity
                .created(URI.create("/api/v1/requests/" + id + "/comments/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "List request comments in chronological order")
    public PageResponse<RequestCommentResponse> findComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return requestService.findComments(id, page, size);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get the immutable status transition history of a request")
    public List<RequestHistoryResponse> findHistory(@PathVariable Long id) {
        return requestService.findHistory(id);
    }
}
