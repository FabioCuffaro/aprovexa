package com.aprovexa.request.persistence;

import com.aprovexa.request.comment.RequestCommentRepository;
import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.TransitionRequest;
import com.aprovexa.request.history.RequestHistoryRepository;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.repository.RequestRepository;
import com.aprovexa.request.service.RequestService;
import com.aprovexa.support.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private RequestHistoryRepository historyRepository;

    @Autowired
    private RequestCommentRepository commentRepository;

    @Autowired
    private RequestService requestService;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE request_comments, request_history, requests RESTART IDENTITY CASCADE");
    }

    @Test
    void flywayCreatesVersionedSchemaOnRealPostgres() {
        Long requests = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='requests'",
                Long.class
        );
        Long history = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='request_history'",
                Long.class
        );
        Long comments = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='request_comments'",
                Long.class
        );
        Long migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Long.class
        );

        assertThat(requests).isEqualTo(1L);
        assertThat(history).isEqualTo(1L);
        assertThat(comments).isEqualTo(1L);
        assertThat(migrations).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void secondFlywayMigrateDoesNotChangeSchemaHistory() {
        Long before = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Long.class
        );

        flyway.migrate();

        Long after = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Long.class
        );
        assertThat(after).isEqualTo(before);
    }

    @Test
    void requestSearchUsesRealPostgresWithFiltersAndStablePagination() {
        requestRepository.save(newRequest(RequestType.PURCHASE, "Laptop A"));
        requestRepository.save(newRequest(RequestType.PURCHASE, "Laptop B"));
        requestRepository.save(newRequest(RequestType.ACCESS, "Repository access"));
        requestRepository.flush();

        var page = requestRepository.search(
                RequestType.PURCHASE,
                RequestStatus.CREATED,
                PageRequest.of(
                        0,
                        2,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.ASC, "id"))
                )
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .allMatch(request -> request.getType() == RequestType.PURCHASE)
                .allMatch(request -> request.getStatus() == RequestStatus.CREATED);
    }

    @Test
    void statusTransitionPersistsImmutableHistory() {
        Long requestId = createRequest();

        requestService.submit(requestId, new TransitionRequest("Laura"));

        var history = historyRepository.findTimelineByRequestId(requestId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getPreviousStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(history.getFirst().getNewStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        assertThat(history.getFirst().getChangedBy()).isEqualTo("Laura");

        Long historyId = history.getFirst().getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE request_history SET changed_by = 'Tampered' WHERE id = ?",
                historyId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void commentsArePersistedAndPaginatedChronologically() {
        Long requestId = createRequest();

        requestService.addComment(requestId, new CreateRequestCommentRequest("Laura", "First comment"));
        requestService.addComment(requestId, new CreateRequestCommentRequest("Manager", "Second comment"));
        requestService.addComment(requestId, new CreateRequestCommentRequest("Laura", "Third comment"));

        var page = requestService.findComments(requestId, 0, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).content()).isEqualTo("First comment");
        assertThat(page.content().get(1).content()).isEqualTo("Second comment");
        assertThat(commentRepository.count()).isEqualTo(3);
    }

    @Test
    void transactionRollsBackStateWhenAuditInsertFails() {
        Long requestId = createRequest();
        String actorTooLongForDatabase = "A".repeat(121);

        assertThatThrownBy(() -> requestService.submit(
                requestId,
                new TransitionRequest(actorTooLongForDatabase)
        )).isInstanceOf(RuntimeException.class);

        Request reloaded = requestRepository.findById(requestId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(historyRepository.findTimelineByRequestId(requestId)).isEmpty();
    }

    @Test
    void databaseConstraintRejectsInvalidRequestStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO requests (
                    type, status, title, description, requester, created_at, updated_at
                ) VALUES (
                    'PURCHASE', 'BROKEN', 'Valid title', 'A sufficiently long description', 'Laura', now(), now()
                )
                """))
                .isInstanceOf(DataAccessException.class);
    }

    private Long createRequest() {
        return requestService.create(new CreateRequestRequest(
                RequestType.PURCHASE,
                "Development laptop",
                "Laptop required for backend development work",
                "Current equipment is insufficient",
                "Laura"
        )).id();
    }

    private Request newRequest(RequestType type, String title) {
        return new Request(
                type,
                title,
                "Description long enough for persistence validation",
                null,
                "Laura"
        );
    }
}
