package com.aprovexa.request.repository;

import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, Long> {

    @Query("""
            select request
            from Request request
            where (:type is null or request.type = :type)
              and (:status is null or request.status = :status)
            """)
    Page<Request> search(
            @Param("type") RequestType type,
            @Param("status") RequestStatus status,
            Pageable pageable
    );

    @Query("""
            select request
            from Request request
            where lower(request.requester) = lower(:requester)
              and (:type is null or request.type = :type)
              and (:status is null or request.status = :status)
            """)
    Page<Request> searchOwned(
            @Param("requester") String requester,
            @Param("type") RequestType type,
            @Param("status") RequestStatus status,
            Pageable pageable
    );
}
