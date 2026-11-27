package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Checks how the correlation id is chosen. */
class CorrelationIdsTest {

    @AfterEach
    void cleanUp() {
        CorrelationIds.clear();
    }

    @Test
    void usesTheValueKeptByTheFilterFirst() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIds.ATTRIBUTE, "kept");
        request.addHeader(CorrelationIds.HEADER, "header");

        assertEquals("kept", CorrelationIds.of(request));
    }

    @Test
    void usesTheHeaderWhenNothingWasKept() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "header");

        assertEquals("header", CorrelationIds.of(request));
    }

    @Test
    void usesTheCurrentThreadValueWhenTheRequestHasNone() {
        CorrelationIds.set("thread");

        assertEquals("thread", CorrelationIds.of(new MockHttpServletRequest()));
        assertEquals("thread", CorrelationIds.of(null));
    }

    @Test
    void createsANewValueWhenThereIsNone() {
        String first = CorrelationIds.current();

        assertNotNull(first);
        assertNotEquals(first, CorrelationIds.current());
    }

    @Test
    void aBlankValueClearsTheCurrentOne() {
        CorrelationIds.set("thread");
        CorrelationIds.set(" ");

        assertNotEquals("thread", CorrelationIds.current());
    }
}
