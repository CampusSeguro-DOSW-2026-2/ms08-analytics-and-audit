package edu.eci.dosw.campusseguro.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.service.LocationAnalyticsCalculator.PlaceStat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the counts and times by building or zone, with plain lists of facts. */
class LocationAnalyticsCalculatorTest {

    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    private final LocationAnalyticsCalculator calculator =
            new LocationAnalyticsCalculator(new EventIndicatorCalculator());
    private int counter;

    private AuditEvent fact(String module, String action, AuditResult result, String user, String resource,
            long seconds, Map<String, Object> attributes) {
        Instant time = START.plusSeconds(seconds);
        return new AuditEvent("f-" + (counter++), user, module, action, resource, result, time, time, "corr",
                null, "e", EventKind.SIMULACRO, attributes, "x");
    }

    private AuditEvent ok(String module, String action, String user, String resource, long seconds,
            Map<String, Object> attributes) {
        return fact(module, action, AuditResult.EXITO, user, resource, seconds, attributes);
    }

    private Map<String, Object> place(String type, String id) {
        return Map.of("ubicacion_tipo", type, "ubicacion_id", id, "prioridad", "ALTA");
    }

    private PlaceStat find(List<PlaceStat> stats, String type, String id) {
        return stats.stream().filter(s -> s.type().equals(type) && s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void countsIncidentsAssistanceAndAttentionTimeByPlace() {
        List<AuditEvent> facts = List.of(
                ok("MS05", "CREAR_INCIDENTE", "u1", "incidente:a", 100, place("EDIFICIO", "b1")),
                ok("MS05", "INICIAR_ATENCION", "br", "incidente:a", 160, null),
                ok("MS05", "CREAR_INCIDENTE", "u2", "incidente:b", 200, place("EDIFICIO", "b1")),
                ok("MS05", "INICIAR_ATENCION", "br", "incidente:b", 500, null),
                ok("MS05", "CREAR_INCIDENTE", "u3", "incidente:c", 300, place("ZONA", "z1")),
                // The same user asks for help twice at b1: counts once. A failed fact is ignored.
                ok("MS04", "REGISTRO_CHECKIN", "u9", "checkin:1", 50,
                        Map.of("estado", "NEEDS_ASSISTANCE", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1")),
                ok("MS04", "CAMBIO_ESTADO_CHECKIN", "u9", "checkin:1", 70,
                        Map.of("estado", "NEEDS_ASSISTANCE", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1")),
                fact("MS04", "REGISTRO_CHECKIN", AuditResult.FALLO, "u8", "evento:e", 60,
                        Map.of("estado", "NEEDS_ASSISTANCE", "ubicacion_tipo", "ZONA", "ubicacion_id", "z1")));

        List<PlaceStat> stats = calculator.compute(facts);

        assertEquals(2, stats.size());
        PlaceStat building = find(stats, "EDIFICIO", "b1");
        assertEquals(2, building.incidents());
        assertEquals(1, building.assistanceRequests());
        assertEquals(360, building.attentionSeconds());
        assertEquals(2, building.samples());
        PlaceStat zone = find(stats, "ZONA", "z1");
        assertEquals(1, zone.incidents());
        assertEquals(0, zone.assistanceRequests());
        assertEquals(0, zone.samples());
    }

    @Test
    void takesThePlaceFromTheAttentionWhenTheReportHasNone() {
        List<AuditEvent> facts = List.of(
                ok("MS05", "CREAR_INCIDENTE", "u1", "incidente:a", 100, Map.of("prioridad", "ALTA")),
                ok("MS05", "INICIAR_ATENCION", "br", "incidente:a", 130,
                        Map.of("ubicacion_tipo", "ZONA", "ubicacion_id", "z2")));

        List<PlaceStat> stats = calculator.compute(facts);

        assertEquals(1, stats.size());
        assertEquals("z2", stats.get(0).id());
        assertEquals(0, stats.get(0).incidents());
        assertEquals(30, stats.get(0).attentionSeconds());
    }

    @Test
    void factsWithoutPlaceAreNotAttributed() {
        List<AuditEvent> facts = List.of(
                ok("MS05", "CREAR_INCIDENTE", "u1", "incidente:a", 100, Map.of("prioridad", "ALTA")),
                ok("MS04", "REGISTRO_CHECKIN", "u2", "checkin:1", 10, Map.of("estado", "NEEDS_ASSISTANCE")));

        assertTrue(calculator.compute(facts).isEmpty());
    }
}
