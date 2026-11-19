package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.service.EventAnalyticsService;
import edu.eci.dosw.campusseguro.audit.service.EventFactsLoader;
import edu.eci.dosw.campusseguro.audit.service.EventIndicatorCalculator;
import edu.eci.dosw.campusseguro.audit.service.LocationAnalyticsCalculator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Checks the validation, the permissions, the ordering and the numbers, with the database mocked. */
class EventAnalyticsServiceTest {

    private static final IndicatorVisibility DRILL = new IndicatorVisibility(true, false);
    private static final IndicatorVisibility REAL = new IndicatorVisibility(false, true);
    private static final IndicatorVisibility NONE = new IndicatorVisibility(false, false);
    private static final String D1 = "11111111-1111-4111-8111-111111111111";
    private static final String D2 = "22222222-2222-4222-8222-222222222222";
    private static final String R1 = "33333333-3333-4333-8333-333333333333";
    private static final Instant NOW = Instant.parse("2026-11-30T12:00:00Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final EventIndicatorCalculator indicators = new EventIndicatorCalculator();
    private final EventAnalyticsService service = new EventAnalyticsServiceImpl(
            new EventFactsLoader(repository), indicators, new LocationAnalyticsCalculator(indicators),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private int counter;

    private AuditEvent fact(String eventId, EventKind kind, String module, String action, String user,
            String resource, Instant time, Map<String, Object> attributes) {
        return new AuditEvent("f-" + (counter++), user, module, action, resource, AuditResult.EXITO, time, time,
                "corr", null, eventId, kind, attributes, "x");
    }

    /**
     * Registers an event: it starts at {@code start}; recipients are notified; {@code checkins} users check
     * in as SAFE, 20 s apart from the 1st second; one incident at building b1 is attended after {@code attention} s.
     */
    private void event(String id, EventKind kind, Instant start, int recipients, int checkins, long attention) {
        List<AuditEvent> facts = new ArrayList<>();
        facts.add(fact(id, kind, "MS06", "EVENT_STARTED", null, "evento:" + id, start, null));
        facts.add(fact(id, kind, "MS03", "NOTIFICATION_SENT", null, "alerta:" + id, start.plusSeconds(1),
                Map.of("destinatarios", recipients, "enviados", recipients, "fallidos", 0)));
        IntStream.range(0, checkins).forEach(i -> facts.add(fact(id, kind, "MS04", "REGISTRO_CHECKIN", "u" + i,
                "checkin:" + id + i, start.plusSeconds(20L * (i + 1)), Map.of("estado", "SAFE"))));
        facts.add(fact(id, kind, "MS05", "CREAR_INCIDENTE", "u0", "incidente:" + id, start.plusSeconds(100),
                Map.of("prioridad", "ALTA", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1")));
        facts.add(fact(id, kind, "MS05", "INICIAR_ATENCION", "br", "incidente:" + id,
                start.plusSeconds(100 + attention), null));
        when(repository.findFirstByEventoId(id)).thenReturn(Optional.of(facts.get(0)));
        when(repository.findByEventoIdAndAccionIn(id, EventIndicatorCalculator.ACTIONS)).thenReturn(facts);
    }

    private void twoDrills() {
        event(D1, EventKind.SIMULACRO, Instant.parse("2026-11-01T10:00:00Z"), 10, 5, 300);
        event(D2, EventKind.SIMULACRO, Instant.parse("2026-11-15T10:00:00Z"), 10, 8, 120);
    }

    @Test
    void comparesEventsOldestFirstWithTheChangeFromTheOneBefore() {
        twoDrills();

        // Asked newest first, the answer is ordered by start.
        EventComparisonResponse response = service.compare(List.of(D2, D1), DRILL);

        assertEquals(2, response.eventos().size());
        EventComparisonResponse.Row first = response.eventos().get(0);
        EventComparisonResponse.Row second = response.eventos().get(1);
        assertEquals(D1, first.eventoId());
        assertEquals(50.0, first.porcentajeParticipacion());
        assertEquals(300.0, first.hastaPrimeraAtencionSegundos());
        assertNull(first.cambioVsAnterior());
        assertEquals(D2, second.eventoId());
        assertEquals(80.0, second.porcentajeParticipacion());
        assertEquals(30.0, second.cambioVsAnterior().porcentajeParticipacion());
        assertEquals(-180.0, second.cambioVsAnterior().hastaPrimeraAtencionSegundos());
    }

    @Test
    void aDuplicatedIdIsCountedOnceAndIsCaseInsensitive() {
        twoDrills();

        assertEquals(2, service.compare(List.of(D1, D2, D1.toUpperCase()), DRILL).eventos().size());
        assertThrows(InvalidFieldException.class, () -> service.compare(List.of(D1, D1.toUpperCase()), DRILL));
    }

    @Test
    void needsBetweenTwoAndTenEvents() {
        twoDrills();

        assertThrows(InvalidFieldException.class, () -> service.compare(List.of(D1), DRILL));
        assertThrows(InvalidFieldException.class, () -> service.compare(null, DRILL));
        assertThrows(InvalidFieldException.class, () -> service.compare(List.of(), DRILL));
        List<String> eleven = IntStream.range(0, 11)
                .mapToObj(i -> String.format("00000000-0000-4000-8000-%012d", i)).toList();
        assertThrows(InvalidFieldException.class, () -> service.compare(eleven, DRILL));
    }

    @Test
    void anInvalidIdGets400() {
        assertThrows(InvalidFieldException.class, () -> service.compare(List.of(D1, "abc"), DRILL));
    }

    @Test
    void withoutPermissionGets403() {
        twoDrills();

        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.compare(List.of(D1, D2), NONE)).code());
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.compare(List.of(D1, D2), REAL)).code());
    }

    @Test
    void anUnknownEventGets404() {
        twoDrills();
        when(repository.findFirstByEventoId(R1)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.compare(List.of(D1, R1), DRILL)).code());
    }

    @Test
    void eventsOfDifferentKindsCannotBeCompared() {
        twoDrills();
        event(R1, EventKind.REAL, Instant.parse("2026-11-20T10:00:00Z"), 10, 5, 60);

        InvalidFieldException error = assertThrows(InvalidFieldException.class,
                () -> service.compare(List.of(D1, R1), new IndicatorVisibility(true, true)));

        assertEquals("evento_id", error.field());
    }

    @Test
    void anEventThatNeverStartedGoesLastAndHasNoTimes() {
        twoDrills();
        String idle = "44444444-4444-4444-8444-444444444444";
        AuditEvent only = fact(idle, EventKind.SIMULACRO, "MS03", "CONFIRM", "u1", "alerta:x", NOW, null);
        when(repository.findFirstByEventoId(idle)).thenReturn(Optional.of(only));
        when(repository.findByEventoIdAndAccionIn(idle, EventIndicatorCalculator.ACTIONS)).thenReturn(List.of(only));

        EventComparisonResponse response = service.compare(List.of(idle, D1), DRILL);

        assertEquals(D1, response.eventos().get(0).eventoId());
        assertEquals(idle, response.eventos().get(1).eventoId());
        assertNull(response.eventos().get(1).hastaCheckinSegundos());
        assertNull(response.eventos().get(1).cambioVsAnterior().hastaCheckinSegundos());
    }

    @Test
    void locationsAddUpTheEventsAndKeepTheDetailByEvent() {
        twoDrills();

        LocationAnalyticsResponse response = service.locations(List.of(D1, D2), DRILL);

        assertEquals(1, response.ubicaciones().size());
        LocationAnalyticsResponse.Location building = response.ubicaciones().get(0);
        assertEquals("EDIFICIO", building.ubicacionTipo());
        assertEquals("b1", building.ubicacionId());
        assertEquals(2, building.incidentes());
        // (300 + 120) / 2
        assertEquals(210.0, building.hastaPrimeraAtencion().promedioSegundos());
        assertEquals(2, building.hastaPrimeraAtencion().muestras());
        assertEquals(2, building.porEvento().size());
        assertEquals(D1, building.porEvento().get(0).eventoId());
        assertEquals(300.0, building.porEvento().get(0).promedioSegundos());
        assertEquals(120.0, building.porEvento().get(1).promedioSegundos());
    }

    @Test
    void locationsAcceptASingleEventAndCheckThePermission() {
        twoDrills();

        assertEquals(1, service.locations(List.of(D1), DRILL).ubicaciones().size());
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BusinessException.class, () -> service.locations(List.of(D1), REAL)).code());
        assertThrows(InvalidFieldException.class, () -> service.locations(List.of(), DRILL));
    }

    @Test
    void locationsAreSortedBySlowestAttentionWithPlacesWithoutSamplesLast() {
        String id = "55555555-5555-4555-8555-555555555555";
        Instant start = Instant.parse("2026-11-10T10:00:00Z");
        List<AuditEvent> facts = List.of(
                fact(id, EventKind.SIMULACRO, "MS06", "EVENT_STARTED", null, "evento:" + id, start, null),
                fact(id, EventKind.SIMULACRO, "MS05", "CREAR_INCIDENTE", "u", "incidente:1", start.plusSeconds(10),
                        Map.of("ubicacion_tipo", "EDIFICIO", "ubicacion_id", "fast")),
                fact(id, EventKind.SIMULACRO, "MS05", "INICIAR_ATENCION", "b", "incidente:1", start.plusSeconds(20), null),
                fact(id, EventKind.SIMULACRO, "MS05", "CREAR_INCIDENTE", "u", "incidente:2", start.plusSeconds(10),
                        Map.of("ubicacion_tipo", "ZONA", "ubicacion_id", "slow")),
                fact(id, EventKind.SIMULACRO, "MS05", "INICIAR_ATENCION", "b", "incidente:2", start.plusSeconds(400), null),
                fact(id, EventKind.SIMULACRO, "MS05", "CREAR_INCIDENTE", "u", "incidente:3", start.plusSeconds(10),
                        Map.of("ubicacion_tipo", "ZONA", "ubicacion_id", "never")));
        when(repository.findFirstByEventoId(id)).thenReturn(Optional.of(facts.get(0)));
        when(repository.findByEventoIdAndAccionIn(id, EventIndicatorCalculator.ACTIONS)).thenReturn(facts);

        List<LocationAnalyticsResponse.Location> places = service.locations(List.of(id), DRILL).ubicaciones();

        assertEquals("slow", places.get(0).ubicacionId());
        assertEquals("fast", places.get(1).ubicacionId());
        assertEquals("never", places.get(2).ubicacionId());
        assertNull(places.get(2).hastaPrimeraAtencion().promedioSegundos());
    }
}
