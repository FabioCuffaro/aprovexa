package com.aprovexa.request.persistence;

import com.aprovexa.request.comment.RequestCommentRepository;
import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
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
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "aprovexa.security.jwt.issuer=aprovexa",
        "aprovexa.security.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "aprovexa.security.jwt.access-token-ttl=PT30M"
})
@Import(TestcontainersConfiguration.class)
@WithMockUser(username = "laura@example.com", roles = "USER")
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
        jdbcTemplate.execute("TRUNCATE TABLE request_comments, request_history, requests, users RESTART IDENTITY CASCADE");
    }

    @Test
    void flywayCreatesVersionedSchemaOnRealPostgres() {
        Long requests = tableCount("requests");
        Long history = tableCount("request_history");
        Long comments = tableCount("request_comments");
        Long users = tableCount("users");
        Long migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Long.class
        );

        assertThat(requests).isEqualTo(1L);
        assertThat(history).isEqualTo(1L);
        assertThat(comments).isEqualTo(1L);
        assertThat(users).isEqualTo(1L);
        assertThat(migrations).isGreaterThanOrEqualTo(3L);
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
        requestRepository.save(newRequest(RequestType.PURCHASE, "Laptop A", "laura@example.com"));
        requestRepository.save(newRequest(RequestType.PURCHASE, "Laptop B", "laura@example.com"));
        requestRepository.save(newRequest(RequestType.ACCESS, "Repository access", "other@example.com"));
        requestRepository.flush();

        var page = requestRepository.searchOwned(
                "laura@example.com",
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
                .allMatch(request -> request.getRequester().equals("laura@example.com"))
                .allMatch(request -> request.getType() == RequestType.PURCHASE)
                .allMatch(request -> request.getStatus() == RequestStatus.CREATED);
    }

    @Test
    void statusTransitionPersistsImmutableHistoryUsingAuthenticatedActor() {
        Long requestId = createRequest();

        requestService.submit(requestId);

        var history = historyRepository.findTimelineByRequestId(requestId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getPreviousStatus()).isEqualTo(RequestStatus.CREATED);
        assertThat(history.getFirst().getNewStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        assertThat(history.getFirst().getChangedBy()).isEqualTo("laura@example.com");

        Long historyId = history.getFirst().getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE request_history SET changed_by = 'Tampered' WHERE id = ?",
                historyId
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void commentsArePersistedAndPaginatedChronologicallyWithAuthenticatedAuthor() {
        Long requestId = createRequest();

        requestService.addComment(requestId, new CreateRequestCommentRequest("First comment"));
        requestService.addComment(requestId, new CreateRequestCommentRequest("Second comment"));
        requestService.addComment(requestId, new CreateRequestCommentRequest("Third comment"));

        var page = requestService.findComments(requestId, 0, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).content()).isEqualTo("First comment");
        assertThat(page.content().get(1).content()).isEqualTo("Second comment");
        assertThat(page.content()).allMatch(comment -> comment.author().equals("laura@example.com"));
        assertThat(commentRepository.count()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "rollback-test@aprovexa.local", roles = "USER")
    void transactionRollsBackStateWhenAuditInsertFails() {
        Long requestId = createRequest();
        installAuditFailureTrigger();

        try {
            assertThatThrownBy(() -> requestService.submit(requestId))
                    .isInstanceOf(RuntimeException.class);

            Request reloaded = requestRepository.findById(requestId).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.CREATED);
            assertThat(historyRepository.findTimelineByRequestId(requestId)).isEmpty();
        } finally {
            removeAuditFailureTrigger();
        }
    }

    @Test
    void databaseConstraintRejectsInvalidRequestStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO requests (
                    type, status, title, description, requester, created_at, updated_at
                ) VALUES (
                    'PURCHASE', 'BROKEN', 'Valid title', 'A sufficiently long description', 'laura@example.com', now(), now()
                )
                """))
                .isInstanceOf(DataAccessException.class);
    }

    private Long tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?",
                Long.class,
                tableName
        );
    }

    private Long createRequest() {
        return requestService.create(new CreateRequestRequest(
                RequestType.PURCHASE,
                "Development laptop",
                "Laptop required for backend development work",
                "Current equipment is insufficient"
        )).id();
    }

    private Request newRequest(RequestType type, String title, String requester) {
        return new Request(
                type,
                title,
                "Description long enough for persistence validation",
                null,
                requester
        );
    }

    private void installAuditFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_request_history_insert_for_test()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF NEW.changed_by = 'rollback-test@aprovexa.local' THEN
                        RAISE EXCEPTION 'forced audit failure for transactional rollback test';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_request_history_test_failure ON request_history");
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_request_history_test_failure
                BEFORE INSERT ON request_history
                FOR EACH ROW
                EXECUTE FUNCTION fail_request_history_insert_for_test()
                """);
    }

    private void removeAuditFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_request_history_test_failure ON request_history");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_request_history_insert_for_test()");
    }
}
