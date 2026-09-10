package com.aprovexa.request.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    @Query("""
            select comment
            from RequestComment comment
            where comment.request.id = :requestId
            """)
    Page<RequestComment> findPageByRequestId(@Param("requestId") Long requestId, Pageable pageable);
}
