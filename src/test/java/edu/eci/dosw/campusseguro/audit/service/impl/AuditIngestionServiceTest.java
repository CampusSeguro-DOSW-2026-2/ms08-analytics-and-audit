package edu.eci.dosw.campusseguro.audit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.audit.config.AuditProperties;
import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.mapper.AuditEventMapper;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/** Checks the contract rules and the idempotent insert, with the database mocked. */
class AuditIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");
    private static final String EVENT = "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditIngestionServiceImpl service = new AuditIngestionServiceImpl(
            repository, new AuditEventMapper(), new AuditProperties(180, true, false),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private AuditEventRequest request(OffsetDateTime time, String eventId, EventKind kind, Map<String, Object> attrs) {
        return new AuditEventRequest(null, null, "MS06", "EVENT_CREATED", "evento:" + EVENT,
                AuditResult.EXITO, time, "corr-1", null, eventId, kind, attrs);
    }

    private AuditEventRequest valid() {
        return request(NOW.atOffset(ZoneOffset.UTC), EVENT, EventKind.SIMULACRO, null);
    }

    @Test
    void storesTheFactWithTheReceivedTimeAndTheOrigin() {
        service.register(valid(), "ms06");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).insert(captor.capture());
        assertEquals(NOW, captor.getValue().recibidoEn());
        assertEquals("ms06", captor.getValue().servicioOrigen());
        assertEquals(NOW, captor.getValue().fechaHora());
    }

    @Test
    void aDuplicateIsIgnoredWithoutError() {
        when(repository.insert(any(AuditEvent.class))).thenThrow(new DuplicateKeyException("dup"));

        service.register(valid(), "ms06");

        verify(repository).insert(any(AuditEvent.class));
    }

    @Test
    void convertsAnOffsetTimeToUtc() {
        OffsetDateTime bogota = NOW.atOffset(ZoneOffset.ofHours(-5));

        service.register(request(bogota, EVENT, EventKind.REAL, null), "ms06");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).insert(captor.capture());
        assertEquals(NOW, captor.getValue().fechaHora());
    }

    @Test
    void rejectsATimeMoreThanFiveMinutesInTheFuture() {
        OffsetDateTime future = NOW.plusSeconds(301).atOffset(ZoneOffset.UTC);

        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(future, EVENT, EventKind.REAL, null), "ms06"));

        assertEquals("fecha_hora", error.field());
        verify(repository, never()).insert(any(AuditEvent.class));
    }

    @Test
    void acceptsATimeUpToFiveMinutesInTheFuture() {
        service.register(request(NOW.plusSeconds(300).atOffset(ZoneOffset.UTC), EVENT, EventKind.REAL, null), "ms06");

        verify(repository).insert(any(AuditEvent.class));
    }

    @Test
    void rejectsATimeOlderThanTheRetention() {
        OffsetDateTime old = NOW.minusSeconds(180L * 86400 + 1).atOffset(ZoneOffset.UTC);

        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(old, EVENT, EventKind.REAL, null), "ms06"));

        assertEquals("fecha_hora", error.field());
    }

    @Test
    void anEventIdNeedsTheEventKind() {
        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(NOW.atOffset(ZoneOffset.UTC), EVENT, null, null), "ms06"));

        assertEquals("tipo_evento", error.field());
    }

    @Test
    void aDrillNeedsAnEventId() {
        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(NOW.atOffset(ZoneOffset.UTC), null, EventKind.SIMULACRO, null), "ms06"));

        assertEquals("tipo_evento", error.field());
    }

    @Test
    void aRealFactWithoutEventIdIsAllowed() {
        service.register(request(NOW.atOffset(ZoneOffset.UTC), null, EventKind.REAL, null), "ms03");
        service.register(request(NOW.atOffset(ZoneOffset.UTC), null, null, null), "ms01");

        verify(repository, org.mockito.Mockito.times(2)).insert(any(AuditEvent.class));
    }

    @Test
    void acceptsFlatAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("destinatarios", 120);
        attrs.put("categoria", "FUEGO");
        attrs.put("es_oficial", true);

        service.register(request(NOW.atOffset(ZoneOffset.UTC), EVENT, EventKind.SIMULACRO, attrs), "ms03");

        verify(repository).insert(any(AuditEvent.class));
    }

    @Test
    void rejectsMoreThanTwentyAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            attrs.put("k" + i, i);
        }

        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(NOW.atOffset(ZoneOffset.UTC), EVENT, EventKind.REAL, attrs), "ms03"));

        assertEquals("atributos", error.field());
    }

    @Test
    void rejectsAKeyThatIsNotSnakeCase() {
        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.register(request(NOW.atOffset(ZoneOffset.UTC), EVENT, EventKind.REAL,
                        Map.of("Participantes", 1)), "ms03"));

        assertEquals("atributos", error.field());
    }

    @Test
    void rejectsNestedNullOrLongValues() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("a", null);

        for (Map<String, Object> attrs : java.util.List.of(
                Map.<String, Object>of("a", Map.of("b", 1)),
                Map.<String, Object>of("a", "x".repeat(101)),
                withNull)) {
            InvalidFieldException error = assertThrows(InvalidFieldException.class,
                    () -> service.register(request(NOW.atOffset(ZoneOffset.UTC), EVENT, EventKind.REAL, attrs), "ms03"));
            assertEquals("atributos.a", error.field());
        }
    }
}
