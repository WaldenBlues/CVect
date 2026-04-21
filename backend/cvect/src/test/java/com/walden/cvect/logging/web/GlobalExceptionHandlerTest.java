package com.walden.cvect.logging.web;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.WebLogFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new LogProperties(),
            new WebLogFormatter());

    @Test
    void mapsMethodAuthorizationDeniedToForbidden() {
        ResponseEntity<Object> response = handler.handleUnhandled(
                new AuthorizationDeniedException("Access Denied"),
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .isInstanceOfSatisfying(GlobalExceptionHandler.ApiErrorResponse.class, body -> {
                    assertThat(body.status()).isEqualTo(403);
                    assertThat(body.error()).isEqualTo("Forbidden");
                });
    }

    @Test
    void mapsAccessDeniedToForbidden() {
        ResponseEntity<Object> response = handler.handleUnhandled(
                new AccessDeniedException("Access Denied"),
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("DELETE", "/api/jds/example");
    }
}
