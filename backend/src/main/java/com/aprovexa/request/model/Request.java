package com.aprovexa.request.model;

import com.aprovexa.common.error.InvalidRequestStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "requests")
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 1000)
    private String justification;

    @Column(nullable = false, length = 120)
    private String requester;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Request() {
        // Constructor requerido por JPA.
    }

    public Request(RequestType type, String title, String description, String justification, String requester) {
        this.type = type;
        this.status = RequestStatus.CREATED;
        this.title = title;
        this.description = description;
        this.justification = justification;
        this.requester = requester;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void update(RequestType type, String title, String description, String justification) {
        ensureStatus(RequestStatus.CREATED, "Only CREATED requests can be edited");
        this.type = type;
        this.title = title;
        this.description = description;
        this.justification = justification;
    }

    public void submit() {
        transition(RequestStatus.CREATED, RequestStatus.IN_REVIEW, "submit");
    }

    public void approve() {
        transition(RequestStatus.IN_REVIEW, RequestStatus.APPROVED, "approve");
    }

    public void reject() {
        transition(RequestStatus.IN_REVIEW, RequestStatus.REJECTED, "reject");
    }

    public void cancel() {
        if (status != RequestStatus.CREATED && status != RequestStatus.IN_REVIEW) {
            throw new InvalidRequestStateException(
                    "Cannot cancel a request in status %s".formatted(status)
            );
        }
        this.status = RequestStatus.CANCELLED;
    }

    public void ensureCanBeDeleted() {
        ensureStatus(RequestStatus.CREATED, "Only CREATED requests can be deleted");
    }

    private void transition(RequestStatus expected, RequestStatus target, String action) {
        if (status != expected) {
            throw new InvalidRequestStateException(
                    "Cannot %s a request in status %s; expected %s".formatted(action, status, expected)
            );
        }
        this.status = target;
    }

    private void ensureStatus(RequestStatus expected, String message) {
        if (status != expected) {
            throw new InvalidRequestStateException("%s. Current status: %s".formatted(message, status));
        }
    }

    public Long getId() {
        return id;
    }

    public RequestType getType() {
        return type;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getJustification() {
        return justification;
    }

    public String getRequester() {
        return requester;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
