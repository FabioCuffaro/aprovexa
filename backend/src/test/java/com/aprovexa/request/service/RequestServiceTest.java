package com.aprovexa.request.service;

import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.request.dto.CreateRequestRequest;
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

    private RequestRepository repository;
    private RequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(RequestRepository.class);
        service = new RequestService(repository);
    }

    @Test
    void createPersistsCreatedRequest() {
        when(repository.save(any(Request.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        verify(repository).save(any(Request.class));
    }

    @Test
    void findByIdThrowsWhenRequestDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void submitDelegatesLifecycleRuleToEntity() {
        Request request = new Request(
                RequestType.ACCESS,
                "Repository access",
                "Access is needed for the assigned project",
                null,
                "Laura"
        );
        when(repository.findById(1L)).thenReturn(Optional.of(request));

        var response = service.submit(1L);

        assertThat(response.status()).isEqualTo(RequestStatus.IN_REVIEW);
    }
}
