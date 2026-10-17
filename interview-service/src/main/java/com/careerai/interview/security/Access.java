package com.careerai.interview.security;

import com.careerai.interview.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class Access {

    public static final String USER_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";

    private Access() { }

    public static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Réservé aux administrateurs.");
        }
    }
}
