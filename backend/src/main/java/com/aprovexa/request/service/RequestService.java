package com.aprovexa.request.service;

import com.aprovexa.request.comment.RequestComment;
import com.aprovexa.request.comment.RequestCommentRepository;
import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.PageResponse;
import com.aprovexa.request.dto.RequestCommentResponse;
import com.aprovexa.request.dto.RequestHistoryResponse;
import com.aprovexa.request.dto.RequestResponse;
import com.aprovexa.request.dto.UpdateRequestRequest;
import com.aprovexa.request.history.RequestHistory;
import com.aprovexa.request.history.RequestHistoryRepository;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.repository.RequestRepository;
import com.aprovexa.request.security.RequestAuthorizationService;
import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.security.CurrentUser;
import com.aprovexa.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
public class RequestService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "createdAt", "updatedAt", "title", "status", "type"
    );

    private final RequestRepository requestRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final RequestCommentRepository requestCommentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final RequestAuthorizationService authorizationService;

    public RequestService(
            RequestRepository requestRepository,
            RequestHistoryRepository requestHistoryRepository,
            RequestCommentRepository requestCommentRepository,
            CurrentUserProvider currentUserProvider,
            RequestAuthorizationService authorizationService
    ) {
        this.requestRepository = requestRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.requestCommentRepository = requestCommentRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public RequestResponse create(CreateRequestRequest input) {
        CurrentUser user = currentUserProvider.get();
        Request request = new Request(
                input.type(),
                input.title().trim(),
                input.description().trim(),
                normalizeNullable(input.justification()),
                user.email()
        );
        return toResponse(requestRepository.save(request));
    }

    public RequestResponse findById(Long id) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanRead(request, user);
        return toResponse(request);
    }

    public PageResponse<RequestResponse> findAll(
            RequestType type,
            RequestStatus status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        CurrentUser user = currentUserProvider.get();
        String safeSortBy = validateSortField(sortBy);
        Sort sort = Sort.by(direction, safeSortBy);
        if (!"id".equals(safeSortBy)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Request> result = authorizationService.canReadAll(user)
                ? requestRepository.search(type, status, pageable)
                : requestRepository.searchOwned(user.email(), type, status, pageable);

        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional
    public RequestResponse update(Long id, UpdateRequestRequest input) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanManage(request, user);
        request.update(
                input.type(),
                input.title().trim(),
                input.description().trim(),
                normalizeNullable(input.justification())
        );
        return toResponse(request);
    }

    @Transactional
    public void delete(Long id) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanManage(request, user);
        request.ensureCanBeDeleted();
        requestRepository.delete(request);
    }

    @Transactional
    public RequestResponse submit(Long id) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanManage(request, user);
        return transition(request, user.email(), Request::submit);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REQUEST_REVIEW')")
    public RequestResponse approve(Long id) {
        CurrentUser user = currentUserProvider.get();
        authorizationService.requireCanReview(user);
        return transition(getEntity(id), user.email(), Request::approve);
    }

    @Transactional
    @PreAuthorize("hasAuthority('REQUEST_REVIEW')")
    public RequestResponse reject(Long id) {
        CurrentUser user = currentUserProvider.get();
        authorizationService.requireCanReview(user);
        return transition(getEntity(id), user.email(), Request::reject);
    }

    @Transactional
    public RequestResponse cancel(Long id) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanManage(request, user);
        return transition(request, user.email(), Request::cancel);
    }

    @Transactional
    public RequestCommentResponse addComment(Long id, CreateRequestCommentRequest input) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanComment(request, user);
        RequestComment comment = new RequestComment(
                request,
                user.email(),
                input.content().trim()
        );
        return toCommentResponse(requestCommentRepository.save(comment));
    }

    public PageResponse<RequestCommentResponse> findComments(Long id, int page, int size) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanComment(request, user);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "createdAt")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
        return PageResponse.from(
                requestCommentRepository.findPageByRequestId(id, pageable)
                        .map(this::toCommentResponse)
        );
    }

    public List<RequestHistoryResponse> findHistory(Long id) {
        CurrentUser user = currentUserProvider.get();
        Request request = getEntity(id);
        authorizationService.requireCanReadHistory(request, user);
        return requestHistoryRepository.findTimelineByRequestId(id).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private RequestResponse transition(Request request, String actor, Consumer<Request> transition) {
        RequestStatus previousStatus = request.getStatus();
        transition.accept(request);
        RequestStatus newStatus = request.getStatus();

        requestHistoryRepository.save(new RequestHistory(
                request,
                previousStatus,
                newStatus,
                actor
        ));

        return toResponse(request);
    }

    private Request getEntity(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new RequestNotFoundException(id));
    }

    private String validateSortField(String sortBy) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Unsupported sort field '%s'. Allowed values: %s".formatted(sortBy, ALLOWED_SORT_FIELDS)
            );
        }
        return sortBy;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private RequestResponse toResponse(Request request) {
        return new RequestResponse(
                request.getId(),
                request.getType(),
                request.getStatus(),
                request.getTitle(),
                request.getDescription(),
                request.getJustification(),
                request.getRequester(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private RequestCommentResponse toCommentResponse(RequestComment comment) {
        return new RequestCommentResponse(
                comment.getId(),
                comment.getRequest().getId(),
                comment.getAuthor(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    private RequestHistoryResponse toHistoryResponse(RequestHistory history) {
        return new RequestHistoryResponse(
                history.getId(),
                history.getRequest().getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedBy(),
                history.getChangedAt()
        );
    }
}
