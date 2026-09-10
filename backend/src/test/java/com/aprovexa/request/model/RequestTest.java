package com.aprovexa.request.model;

import com.aprovexa.common.error.InvalidRequestStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestTest {

    @Test
    void newRequestStartsInCreatedStatus() {
        Request request = newRequest();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CREATED);
    }

    @Test
    void submitMovesCreatedRequestToInReview() {
        Request request = newRequest();

        request.submit();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);
    }

    @Test
    void approveMovesInReviewRequestToApproved() {
        Request request = newRequest();
        request.submit();

        request.approve();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void rejectMovesInReviewRequestToRejected() {
        Request request = newRequest();
        request.submit();

        request.reject();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    void cancelIsAllowedWhileRequestIsUnresolved() {
        Request created = newRequest();
        Request inReview = newRequest();
        inReview.submit();

        created.cancel();
        inReview.cancel();

        assertThat(created.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(inReview.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void approveCreatedRequestReturnsConflictRule() {
        Request request = newRequest();

        assertThatThrownBy(request::approve)
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("expected IN_REVIEW");
    }

    @Test
    void resolvedRequestCannotBeCancelled() {
        Request request = newRequest();
        request.submit();
        request.approve();

        assertThatThrownBy(request::cancel)
                .isInstanceOf(InvalidRequestStateException.class)
                .hasMessageContaining("Cannot cancel");
    }

    @Test
    void onlyCreatedRequestCanBeEditedOrDeleted() {
        Request request = newRequest();
        request.submit();

        assertThatThrownBy(() -> request.update(
                RequestType.ACCESS,
                "Updated title",
                "Updated description with enough length",
                null
        )).isInstanceOf(InvalidRequestStateException.class);

        assertThatThrownBy(request::ensureCanBeDeleted)
                .isInstanceOf(InvalidRequestStateException.class);
    }

    private Request newRequest() {
        return new Request(
                RequestType.PURCHASE,
                "Development laptop",
                "Laptop required for backend development work",
                "Current equipment is insufficient",
                "Laura"
        );
    }
}
