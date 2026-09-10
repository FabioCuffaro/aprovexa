package com.aprovexa.auth.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Role {
    USER(EnumSet.of(
            Permission.REQUEST_CREATE,
            Permission.REQUEST_READ_OWN,
            Permission.REQUEST_UPDATE_OWN,
            Permission.REQUEST_DELETE_OWN,
            Permission.REQUEST_SUBMIT_OWN,
            Permission.REQUEST_CANCEL_OWN,
            Permission.REQUEST_COMMENT_OWN,
            Permission.REQUEST_HISTORY_OWN
    )),
    MANAGER(EnumSet.of(
            Permission.REQUEST_CREATE,
            Permission.REQUEST_READ_OWN,
            Permission.REQUEST_READ_ALL,
            Permission.REQUEST_UPDATE_OWN,
            Permission.REQUEST_DELETE_OWN,
            Permission.REQUEST_SUBMIT_OWN,
            Permission.REQUEST_CANCEL_OWN,
            Permission.REQUEST_REVIEW,
            Permission.REQUEST_COMMENT_OWN,
            Permission.REQUEST_COMMENT_ALL,
            Permission.REQUEST_HISTORY_OWN,
            Permission.REQUEST_HISTORY_ALL
    )),
    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
