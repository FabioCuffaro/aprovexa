package com.aprovexa.request.service;

import com.aprovexa.auth.model.Role;
import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.request.comment.RequestCommentRepository;
import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.history.RequestHistory;
import com.aprovexa.request.history.RequestHistoryRepository;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.repository.RequestRepository;
import com.aprovexa.request.security.RequestAuthorizationService;
import com.aprovexa.security.CurrentUser;
import com.aprovexa.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestServiceTest {

    private RequestRepository requestRepository;
    private RequestHistoryRepository historyRepository;
    private RequestCommentRepository commentRepository;
    private CurrentUserProvider currentUserProvider;
    private RequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(RequestRepository.class);
        historyRepository = mock(RequestHistoryRepository.class);
        commentRepository = mock(RequestCommentRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.get()).thenReturn(user("laura@example.com", Role.USER));

        service = new RequestService(
                requestRepository,
                historyRepository,
                commentRepository,
                currentUserProvider,
                new RequestAuthorizationService()
        );
    }

    @Test
    void createUsesAuthenticatedIdentityAsOwner() {
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateRequestRequest(
                RequestType.PURCHASE,
                "  Development laptop  ",
                "  Laptop required for backend development  ",
                "  Current equipment is insufficient  "
        ));

        assertThat(response.status()).isEqualTo(RequestStatus.CREATED);
        assertThat(response.title()).isEqualTo("Development laptop");
        assertThat(response.requester()).isEqualTo("laura@example.com");
        verify(requestRepository).save(any(Request.class));
    }

    @Test
    void findByIdThrowsWhenRequestDoesNotExist() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void submitWritesAuthenticatedUserToHistory() {
        Request request = ownedRequest("laura@example.com");
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(historyRepository.save(any(RequestHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(1L);

        assertThat(response.status()).isEqualTo(RequestStatus.IN_REVIEW);
        verify(historyRepository).save(org.mockito.ArgumentMatchers.argThat(history ->
                history.getChangedBy().equals("laura@example.com")
                        && history.getPreviousStatus() == RequestStatus.CREATED
                        && history.getNewStatus() == RequestStatus.IN_REVIEW
        ));
    }

    @Test
    void addCommentUsesAuthenticatedUserAsAuthor() {
        Request request = ownedRequest("laura@example.com");
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(commentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.addComment(1L, new CreateRequestCommentRequest(
                "  Please include the expected delivery date.  "
        ));

        assertThat(response.author()).isEqualTo("laura@example.com");
        assertThat(response.content()).isEqualTo("Please include the expected delivery date.");
        verify(commentRepository).save(any());
    }

    private CurrentUser user(String email, Role role) {
        return new CurrentUser(email, role, role.permissions());
    }

    private Request ownedRequest(String email) {
        return new Request(
                RequestType.ACCESS,
                "Repository access",
                "Access is needed for the assigned project",
                null,
                email
        );
    }
}
