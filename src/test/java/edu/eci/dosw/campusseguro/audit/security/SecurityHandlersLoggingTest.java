package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import edu.eci.dosw.campusseguro.audit.LogCapture;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** The 401 and 403 produced by Spring Security (before the controllers) are logged as warnings. */
class SecurityHandlersLoggingTest {

    private final ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/audit/events");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void theEntryPointLogsAWarningAndWritesTheError() throws IOException {
        try (LogCapture logs = new LogCapture(RestAuthenticationEntryPoint.class, Level.INFO)) {
            new RestAuthenticationEntryPoint(writer).commence(request, response, new BadCredentialsException("x"));

            assertEquals(Level.WARN, logs.events().get(0).getLevel());
            assertTrue(logs.text().contains("Unauthenticated request: POST /api/v1/audit/events"));
            verify(writer).write(request, response, ErrorCode.UNAUTHORIZED);
        }
    }

    @Test
    void theAccessDeniedHandlerLogsAWarningAndWritesTheError() throws IOException {
        try (LogCapture logs = new LogCapture(RestAccessDeniedHandler.class, Level.INFO)) {
            new RestAccessDeniedHandler(writer).handle(request, response, new AccessDeniedException("x"));

            assertEquals(Level.WARN, logs.events().get(0).getLevel());
            assertTrue(logs.text().contains("Forbidden request: POST /api/v1/audit/events"));
            verify(writer).write(request, response, ErrorCode.FORBIDDEN);
        }
    }
}
