package edu.eci.dosw.campusseguro.audit.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import edu.eci.dosw.campusseguro.audit.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** Checks what is logged when a request is rejected: status, code and field, never the values sent. */
class RequestRejectionLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/analytics/events/x");

    @Test
    void aBusinessRejectionLogsStatusAndCodeButNotTheMessage() {
        try (LogCapture logs = new LogCapture(GlobalExceptionHandler.class, Level.INFO)) {
            handler.handleBusiness(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "valor-secreto-123"), request);

            assertEquals(1, logs.events().size());
            assertEquals(Level.INFO, logs.events().get(0).getLevel());
            assertTrue(logs.text().contains("GET /api/v1/analytics/events/x -> 404 NO_ENCONTRADO"));
            assertFalse(logs.text().contains("valor-secreto-123"));
        }
    }

    @Test
    void anInvalidFieldLogsTheFieldNameButNotTheMessage() {
        try (LogCapture logs = new LogCapture(GlobalExceptionHandler.class, Level.INFO)) {
            handler.handleInvalidField(new InvalidFieldException("fecha_hora", "valor-secreto-123"), request);

            assertTrue(logs.text().contains("(field=fecha_hora)"));
            assertTrue(logs.text().contains("400 VALIDACION"));
            assertFalse(logs.text().contains("valor-secreto-123"));
        }
    }

    @Test
    void forbiddenAndUnauthenticatedRequestsAreWarnings() {
        try (LogCapture logs = new LogCapture(GlobalExceptionHandler.class, Level.INFO)) {
            handler.handleAccessDenied(new AccessDeniedException("detalle-interno"), request);
            handler.handleAuthentication(new BadCredentialsException("detalle-interno"), request);

            assertEquals(2, logs.events().size());
            assertEquals(Level.WARN, logs.events().get(0).getLevel());
            assertEquals(Level.WARN, logs.events().get(1).getLevel());
            assertTrue(logs.text().contains("Forbidden request: GET /api/v1/analytics/events/x"));
            assertTrue(logs.text().contains("Unauthenticated request: GET /api/v1/analytics/events/x"));
            assertFalse(logs.text().contains("detalle-interno"));
        }
    }
}
