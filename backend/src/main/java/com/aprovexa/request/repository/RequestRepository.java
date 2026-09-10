package com.aprovexa.request.repository;

import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {

    Page<Request> findByTypeAndStatus(RequestType type, RequestStatus status, Pageable pageable);

    Page<Request> findByType(RequestType type, Pageable pageable);

    Page<Request> findByStatus(RequestStatus status, Pageable pageable);
}
