package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Checks that the filter keeps, creates and cleans the correlation id. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void repeatsTheIdSentByTheGateway() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInChain = new String[1];

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain[0] = MDC.get(CorrelationIds.MDC_KEY);
            }
        });

        assertEquals("abc-123", response.getHeader(CorrelationIds.HEADER));
        assertEquals("abc-123", request.getAttribute(CorrelationIds.ATTRIBUTE));
        assertEquals("abc-123", seenInChain[0]);
    }

    @Test
    void createsAnIdWhenTheHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(CorrelationIds.HEADER));
    }

    @Test
    void cleansTheLogContextAfterTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "abc-123");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }
}
