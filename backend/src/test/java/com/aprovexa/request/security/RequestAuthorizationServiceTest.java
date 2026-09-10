package com.aprovexa.request.security;

import com.aprovexa.auth.model.Role;
import com.aprovexa.request.model.Request;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestAuthorizationServiceTest {

    private final RequestAuthorizationService authorization = new RequestAuthorizationService();

    @Test
    void ownerCanReadAndManageOwnRequest() {
        Request request = requestOwnedBy("user@example.com");
        CurrentUser user = currentUser("user@example.com", Role.USER);

        authorization.requireCanRead(request, user);
        authorization.requireCanManage(request, user);
        assertThat(authorization.canReadAll(user)).isFalse();
    }

    @Test
    void userCannotReadAnotherUsersRequest() {
        Request request = requestOwnedBy("owner@example.com");
        CurrentUser other = currentUser("other@example.com", Role.USER);

        assertThatThrownBy(() -> authorization.requireCanRead(request, other))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerCanReadAndReviewAnyRequestButCannotModifyAnotherOwnersDraft() {
        Request request = requestOwnedBy("owner@example.com");
        CurrentUser manager = currentUser("manager@example.com", Role.MANAGER);

        authorization.requireCanRead(request, manager);
        authorization.requireCanReview(manager);
        assertThat(authorization.canReadAll(manager)).isTrue();
        assertThatThrownBy(() -> authorization.requireCanManage(request, manager))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanManageAnyRequest() {
        Request request = requestOwnedBy("owner@example.com");
        CurrentUser admin = currentUser("admin@example.com", Role.ADMIN);

        authorization.requireCanRead(request, admin);
        authorization.requireCanManage(request, admin);
        authorization.requireCanReview(admin);
    }

    @Test
    void userCannotReviewRequests() {
        CurrentUser user = currentUser("user@example.com", Role.USER);

        assertThatThrownBy(() -> authorization.requireCanReview(user))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Request requestOwnedBy(String email) {
        return new Request(
                RequestType.PURCHASE,
                "Development laptop",
                "Laptop required for backend development work",
                null,
                email
        );
    }

    private CurrentUser currentUser(String email, Role role) {
        return new CurrentUser(email, role, role.permissions());
    }
}
