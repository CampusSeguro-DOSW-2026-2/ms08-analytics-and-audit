package edu.eci.dosw.campusseguro.audit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import edu.eci.dosw.campusseguro.audit.LogCapture;
import edu.eci.dosw.campusseguro.audit.config.AuditProperties;
import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.mapper.AuditEventMapper;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** The audited facts are not logged with their content: only id, module and action (and only at DEBUG). */
class AuditIngestionLoggingTest {

    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditIngestionServiceImpl service = new AuditIngestionServiceImpl(
            repository, new AuditEventMapper(), new AuditProperties(180, true, false),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private AuditEventRequest request() {
        return new AuditEventRequest(null, "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b", "MS04", "REGISTRO_CHECKIN",
                "checkin:abc", AuditResult.EXITO, NOW.atOffset(ZoneOffset.UTC), "corr-1", "detalle-secreto",
                "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", EventKind.SIMULACRO, Map.of("estado", "valor-secreto"));
    }

    @Test
    void aStoredFactIsLoggedWithoutItsContent() {
        try (LogCapture logs = new LogCapture(AuditIngestionServiceImpl.class, Level.DEBUG)) {
            service.register(request(), "ms04");

            assertEquals(1, logs.events().size());
            assertTrue(logs.text().contains("module=MS04 action=REGISTRO_CHECKIN"));
            assertFalse(logs.text().contains("detalle-secreto"));
            assertFalse(logs.text().contains("valor-secreto"));
            assertFalse(logs.text().contains("3f2a9c1e"));
        }
    }

    @Test
    void aDuplicateIsLoggedWithoutItsContent() {
        when(repository.insert(any(AuditEvent.class))).thenThrow(new DuplicateKeyException("dup"));

        try (LogCapture logs = new LogCapture(AuditIngestionServiceImpl.class, Level.DEBUG)) {
            service.register(request(), "ms04");

            assertTrue(logs.text().contains("already stored"));
            assertFalse(logs.text().contains("valor-secreto"));
        }
    }

    @Test
    void nothingIsLoggedAtInfoPerFactToKeepTheLogsSmall() {
        try (LogCapture logs = new LogCapture(AuditIngestionServiceImpl.class, Level.INFO)) {
            service.register(request(), "ms04");

            assertTrue(logs.events().isEmpty());
        }
    }
}
