package edu.eci.dosw.campusseguro.audit.exception;

import edu.eci.dosw.campusseguro.audit.security.CorrelationIds;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers {@code /error} with the standard {@link ErrorResponse}. Errors that never reach a
 * controller would otherwise come out in Boot's own format, and the Gateway turns a non-JSON
 * error into a 502.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value ? value : 500;
        ErrorCode code = ErrorCode.fromStatus(status);

        ErrorResponse body = ErrorResponse.of(code, code.defaultMessage(), List.of(), CorrelationIds.of(request));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
