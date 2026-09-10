package com.aprovexa.request.history;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RequestHistoryRepository extends Repository<RequestHistory, Long> {

    RequestHistory save(RequestHistory history);

    @Query("""
            select history
            from RequestHistory history
            where history.request.id = :requestId
            order by history.changedAt asc, history.id asc
            """)
    List<RequestHistory> findTimelineByRequestId(@Param("requestId") Long requestId);
}
