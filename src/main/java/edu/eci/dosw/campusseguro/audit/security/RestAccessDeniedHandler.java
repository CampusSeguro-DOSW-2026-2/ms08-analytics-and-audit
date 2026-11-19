package edu.eci.dosw.campusseguro.audit.security;

import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Answers 403 in the standard error format when the user lacks the permission. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        log.warn("Forbidden request: {} {}", request.getMethod(), request.getRequestURI());
        errorResponseWriter.write(request, response, ErrorCode.FORBIDDEN);
    }
}
