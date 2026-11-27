package edu.eci.dosw.campusseguro.audit.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Checks the id of a record: the one sent, or a hash that is the same for the same fact. */
class AuditEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");
    private static final OffsetDateTime TIME = NOW.atOffset(ZoneOffset.UTC);

    private final AuditEventMapper mapper = new AuditEventMapper();

    private AuditEventRequest request(String id, String action, String user) {
        return new AuditEventRequest(id, user, "MS01", action, "usuario:abc", AuditResult.EXITO, TIME,
                "corr-1", null, null, null, null);
    }

    @Test
    void keepsTheIdSentByTheServiceInLowerCase() {
        AuditEvent event = mapper.toEntity(
                request("7D444840-9DC0-41D8-8D1C-2D5C6B9A4F10", "LOGIN", null), NOW, "ms01");

        assertEquals("7d444840-9dc0-41d8-8d1c-2d5c6b9a4f10", event.id());
    }

    @Test
    void theSameFactGetsTheSameHash() {
        String first = mapper.toEntity(request(null, "LOGIN", null), NOW, "ms01").id();
        String again = mapper.toEntity(request(null, "LOGIN", null), NOW.plusSeconds(9), "ms01").id();

        assertEquals(first, again);
        assertEquals(64, first.length());
    }

    @Test
    void aDifferentFactGetsADifferentHash() {
        String login = mapper.toEntity(request(null, "LOGIN", null), NOW, "ms01").id();
        String logout = mapper.toEntity(request(null, "LOGOUT", null), NOW, "ms01").id();

        assertNotEquals(login, logout);
    }

    @Test
    void lowersTheUserIdAndKeepsNullAttributes() {
        AuditEvent event = mapper.toEntity(
                request(null, "LOGIN", "3F2A9C1E-7B4D-4E8A-9A55-0C1D2E3F4A5B"), NOW, "ms01");

        assertEquals("3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b", event.usuarioId());
        assertNull(event.atributos());
        assertNull(event.eventoId());
    }
}
