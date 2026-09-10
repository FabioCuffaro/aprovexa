package com.aprovexa.request.service;

import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.request.comment.RequestCommentRepository;
import com.aprovexa.request.dto.CreateRequestCommentRequest;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.TransitionRequest;
import com.aprovexa.request.history.RequestHistory;
import com.aprovexa.request.history.RequestHistoryRepository;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.repository.RequestRepository;
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
    private RequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(RequestRepository.class);
        historyRepository = mock(RequestHistoryRepository.class);
        commentRepository = mock(RequestCommentRepository.class);
        service = new RequestService(requestRepository, historyRepository, commentRepository);
    }

    @Test
    void createPersistsCreatedRequest() {
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateRequestRequest(
                RequestType.PURCHASE,
                "  Development laptop  ",
                "  Laptop required for backend development  ",
                "  Current equipment is insufficient  ",
                "  Laura  "
        ));

        assertThat(response.status()).isEqualTo(RequestStatus.CREATED);
        assertThat(response.title()).isEqualTo("Development laptop");
        assertThat(response.requester()).isEqualTo("Laura");
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
    void submitDelegatesLifecycleRuleAndWritesHistory() {
        Request request = new Request(
                RequestType.ACCESS,
                "Repository access",
                "Access is needed for the assigned project",
                null,
                "Laura"
        );
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        var response = service.submit(1L, new TransitionRequest("  Laura  "));

        assertThat(response.status()).isEqualTo(RequestStatus.IN_REVIEW);
        verify(historyRepository).save(any(RequestHistory.class));
    }

    @Test
    void addCommentPersistsCommentAgainstExistingRequest() {
        Request request = new Request(
                RequestType.PURCHASE,
                "Development laptop",
                "Laptop required for backend development work",
                null,
                "Laura"
        );
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(commentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.addComment(1L, new CreateRequestCommentRequest(
                "  Manager  ",
                "  Please include the expected delivery date.  "
        ));

        assertThat(response.author()).isEqualTo("Manager");
        assertThat(response.content()).isEqualTo("Please include the expected delivery date.");
        verify(commentRepository).save(any());
    }
}
