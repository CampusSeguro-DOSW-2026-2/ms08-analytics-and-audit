package edu.eci.dosw.campusseguro.audit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reuses the {@code X-Correlation-ID} sent by the Gateway (or creates one), puts it in the
 * response header and in the log context.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String value = request.getHeader(CorrelationIds.HEADER);
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        }
        request.setAttribute(CorrelationIds.ATTRIBUTE, value);
        CorrelationIds.set(value);
        MDC.put(CorrelationIds.MDC_KEY, value);
        response.setHeader(CorrelationIds.HEADER, value);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIds.clear();
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
