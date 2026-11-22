package edu.eci.dosw.campusseguro.audit.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Correlation id of the current request. The Gateway assigns the {@code X-Correlation-ID}
 * header; MS08 keeps it for the logs and repeats it in every error answer.
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";

    /** Request attribute where the filter keeps the value (still there in error dispatches). */
    public static final String ATTRIBUTE = CorrelationIds.class.getName() + ".value";

    /** Logging (MDC) key, so every log line shows the correlation id. */
    public static final String MDC_KEY = "correlation_id";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIds() {
    }

    /** Value of the current thread, or a new one when there is none. */
    public static String current() {
        String value = CURRENT.get();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    /** Correlation id of a request: the value the filter kept, the received header, or a new one. */
    public static String of(HttpServletRequest request) {
        if (request != null) {
            Object kept = request.getAttribute(ATTRIBUTE);
            if (kept instanceof String value && !value.isBlank()) {
                return value;
            }
            String header = request.getHeader(HEADER);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return current();
    }

    public static void set(String value) {
        if (value == null || value.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(value);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
