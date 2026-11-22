package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.service.EventIndicatorService;
import edu.eci.dosw.campusseguro.audit.service.EventFactsLoader;
import edu.eci.dosw.campusseguro.audit.service.EventIndicatorCalculator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.BusinessException;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the permission by event kind and the errors, with the database mocked. */
class EventIndicatorServiceTest {

    private static final IndicatorVisibility DRILL = new IndicatorVisibility(true, false);
    private static final IndicatorVisibility REAL = new IndicatorVisibility(false, true);
    private static final IndicatorVisibility NONE = new IndicatorVisibility(false, false);
    private static final String EVENT = "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final EventIndicatorService service = new EventIndicatorServiceImpl(
            new EventFactsLoader(repository), new EventIndicatorCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));

    private AuditEvent fact(EventKind kind) {
        return new AuditEvent("f1", null, "MS06", "EVENT_STARTED", "evento:" + EVENT, AuditResult.EXITO,
                NOW.minusSeconds(60), NOW, "corr", null, EVENT, kind, null, "ms06");
    }

    @Test
    void aUserWithoutIndicatorPermissionGets403BeforeAnyRead() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.forEvent(EVENT, NONE));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        verify(repository, never()).findFirstByEventoId(anyString());
    }

    @Test
    void anInvalidIdIsRejected() {
        assertThrows(InvalidFieldException.class, () -> service.forEvent("not-a-uuid", DRILL));
    }

    @Test
    void anEventWithoutFactsGets404() {
        when(repository.findFirstByEventoId(EVENT)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.forEvent(EVENT, DRILL));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.code());
    }

    @Test
    void aDrillNeedsThePermissionOfDrills() {
        when(repository.findFirstByEventoId(EVENT)).thenReturn(Optional.of(fact(EventKind.SIMULACRO)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.forEvent(EVENT, REAL));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        verify(repository, never()).findByEventoIdAndAccionIn(anyString(), any());
    }

    @Test
    void aRealEventNeedsThePermissionOfRealEvents() {
        when(repository.findFirstByEventoId(EVENT)).thenReturn(Optional.of(fact(EventKind.REAL)));

        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.forEvent(EVENT, DRILL)).code());
    }

    @Test
    void computesWithTheFactsOfTheEventUsingALowercaseId() {
        when(repository.findFirstByEventoId(EVENT)).thenReturn(Optional.of(fact(EventKind.SIMULACRO)));
        when(repository.findByEventoIdAndAccionIn(EVENT, EventIndicatorCalculator.ACTIONS))
                .thenReturn(List.of(fact(EventKind.SIMULACRO)));

        EventIndicatorsResponse response = service.forEvent(EVENT.toUpperCase(), DRILL);

        assertEquals(EVENT, response.eventoId());
        assertEquals(EventKind.SIMULACRO, response.tipoEvento());
        assertEquals(NOW, response.calculadoEn());
        assertEquals(NOW.minusSeconds(60), response.inicioEvento());
    }
}
