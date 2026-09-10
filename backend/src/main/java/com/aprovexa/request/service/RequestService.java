package com.aprovexa.request.service;

import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.PageResponse;
import com.aprovexa.request.dto.RequestResponse;
import com.aprovexa.request.dto.UpdateRequestRequest;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.repository.RequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class RequestService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "createdAt", "updatedAt", "title", "status", "type"
    );

    private final RequestRepository requestRepository;

    public RequestService(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Transactional
    public RequestResponse create(CreateRequestRequest input) {
        Request request = new Request(
                input.type(),
                input.title().trim(),
                input.description().trim(),
                normalizeNullable(input.justification()),
                input.requester().trim()
        );
        return toResponse(requestRepository.save(request));
    }

    public RequestResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    public PageResponse<RequestResponse> findAll(
            RequestType type,
            RequestStatus status,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        String safeSortBy = validateSortField(sortBy);
        Sort sort = Sort.by(direction, safeSortBy);
        if (!"id".equals(safeSortBy)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Request> result;
        if (type != null && status != null) {
            result = requestRepository.findByTypeAndStatus(type, status, pageable);
        } else if (type != null) {
            result = requestRepository.findByType(type, pageable);
        } else if (status != null) {
            result = requestRepository.findByStatus(status, pageable);
        } else {
            result = requestRepository.findAll(pageable);
        }

        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional
    public RequestResponse update(Long id, UpdateRequestRequest input) {
        Request request = getEntity(id);
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
        Request request = getEntity(id);
        request.ensureCanBeDeleted();
        requestRepository.delete(request);
    }

    @Transactional
    public RequestResponse submit(Long id) {
        Request request = getEntity(id);
        request.submit();
        return toResponse(request);
    }

    @Transactional
    public RequestResponse approve(Long id) {
        Request request = getEntity(id);
        request.approve();
        return toResponse(request);
    }

    @Transactional
    public RequestResponse reject(Long id) {
        Request request = getEntity(id);
        request.reject();
        return toResponse(request);
    }

    @Transactional
    public RequestResponse cancel(Long id) {
        Request request = getEntity(id);
        request.cancel();
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
}
