package com.aprovexa.request.security;

import com.aprovexa.auth.model.Permission;
import com.aprovexa.request.model.Request;
import com.aprovexa.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class RequestAuthorizationService {

    public boolean canReadAll(CurrentUser user) {
        return user.has(Permission.REQUEST_READ_ALL);
    }

    public void requireCanRead(Request request, CurrentUser user) {
        if (isOwner(request, user) || user.has(Permission.REQUEST_READ_ALL)) {
            return;
        }
        throw denied();
    }

    public void requireCanManage(Request request, CurrentUser user) {
        if (isOwner(request, user) || user.has(Permission.REQUEST_MANAGE_ALL)) {
            return;
        }
        throw denied();
    }

    public void requireCanReview(CurrentUser user) {
        if (user.has(Permission.REQUEST_REVIEW)) {
            return;
        }
        throw denied();
    }

    public void requireCanComment(Request request, CurrentUser user) {
        if (isOwner(request, user) || user.has(Permission.REQUEST_COMMENT_ALL)) {
            return;
        }
        throw denied();
    }

    public void requireCanReadHistory(Request request, CurrentUser user) {
        if (isOwner(request, user) || user.has(Permission.REQUEST_HISTORY_ALL)) {
            return;
        }
        throw denied();
    }

    private boolean isOwner(Request request, CurrentUser user) {
        return request.getRequester().equalsIgnoreCase(user.email());
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Access denied by request ownership or role policy");
    }
}
