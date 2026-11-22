package edu.eci.dosw.campusseguro.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks every indicator rule with plain lists of facts (no database). */
class EventIndicatorCalculatorTest {

    private static final String EVENT = "11111111-1111-4111-8111-111111111111";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    private final EventIndicatorCalculator calculator = new EventIndicatorCalculator();
    private int counter;

    private AuditEvent fact(String module, String action, AuditResult result, String user, String resource,
            long secondsAfterStart, Map<String, Object> attributes) {
        Instant time = START.plusSeconds(secondsAfterStart);
        return new AuditEvent("f-" + (counter++), user, module, action, resource, result, time, time,
                "corr", null, EVENT, EventKind.SIMULACRO, attributes, "x");
    }

    private AuditEvent ok(String module, String action, String user, String resource, long seconds,
            Map<String, Object> attributes) {
        return fact(module, action, AuditResult.EXITO, user, resource, seconds, attributes);
    }

    private EventIndicatorsResponse compute(List<AuditEvent> facts) {
        return calculator.compute(EVENT, EventKind.SIMULACRO, facts, NOW);
    }

    /** One drill with notifications, check-ins and incidents. */
    private List<AuditEvent> drill() {
        List<AuditEvent> facts = new ArrayList<>();
        facts.add(ok("MS06", "EVENT_STARTED", null, "evento:" + EVENT, 0, Map.of("automatico", false)));
        // Notifications: 4 + 2 recipients, 5 sent, 1 failed; u1 and u2 confirm (u2 twice).
        facts.add(ok("MS03", "NOTIFICATION_SENT", null, "alerta:a1", 5,
                Map.of("destinatarios", 4, "enviados", 4, "fallidos", 0)));
        facts.add(fact("MS03", "NOTIFICATION_FAILED", AuditResult.FALLO, null, "alerta:a2", 6,
                Map.of("destinatarios", 2, "enviados", 1, "fallidos", 1)));
        facts.add(ok("MS03", "CONFIRM", "u1", "alerta:a1", 20, null));
        facts.add(ok("MS03", "CONFIRM", "u2", "alerta:a1", 25, null));
        facts.add(ok("MS03", "CONFIRM", "u2", "alerta:a1", 26, null));
        // Check-ins: u1 evacuating (30s) then safe (90s); u2 safe (60s); u3 needs help (120s).
        facts.add(ok("MS04", "REGISTRO_CHECKIN", "u1", "checkin:c1", 30, Map.of("estado", "EVACUATING")));
        facts.add(ok("MS04", "CAMBIO_ESTADO_CHECKIN", "u1", "checkin:c1", 90,
                Map.of("estado", "SAFE", "punto_encuentro_id", "p1")));
        facts.add(ok("MS04", "REGISTRO_CHECKIN", "u2", "checkin:c2", 60,
                Map.of("estado", "SAFE", "punto_encuentro_id", "p1")));
        facts.add(ok("MS04", "REGISTRO_CHECKIN", "u3", "checkin:c3", 120, Map.of("estado", "NEEDS_ASSISTANCE")));
        facts.add(fact("MS04", "REGISTRO_CHECKIN", AuditResult.FALLO, "u4", "evento:" + EVENT, 130,
                Map.of("estado", "SAFE")));
        // Incidents: A (attended in 60s), B (attended in 300s), C (never attended).
        facts.add(ok("MS05", "CREAR_INCIDENTE", "u1", "incidente:a", 100,
                Map.of("prioridad", "ALTA", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1")));
        facts.add(ok("MS05", "INICIAR_ATENCION", "br1", "incidente:a", 160, null));
        facts.add(ok("MS05", "CREAR_INCIDENTE", "u2", "incidente:b", 200,
                Map.of("prioridad", "ALTA", "ubicacion_tipo", "ZONA", "ubicacion_id", "z1")));
        facts.add(ok("MS05", "INICIAR_ATENCION", "br1", "incidente:b", 500, null));
        facts.add(ok("MS05", "CREAR_INCIDENTE", "u3", "incidente:c", 300,
                Map.of("prioridad", "BAJA", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1")));
        return facts;
    }

    @Test
    void computesNotificationAndConfirmation() {
        EventIndicatorsResponse.Notification n = compute(drill()).notificacion();

        assertEquals(6, n.destinatarios());
        assertEquals(5, n.enviados());
        assertEquals(1, n.fallidos());
        assertEquals(2, n.confirmados());
        assertEquals(33.3, n.porcentajeConfirmacion());
    }

    @Test
    void computesParticipationByLastStateAndMeetingPoint() {
        EventIndicatorsResponse.Participation p = compute(drill()).participacion();

        assertEquals(3, p.participantes());
        assertEquals(50.0, p.porcentajeParticipacion());
        assertEquals(2, p.porEstado().size());
        assertEquals("SAFE", p.porEstado().get(0).clave());
        assertEquals(2, p.porEstado().get(0).cantidad());
        assertEquals(66.7, p.porEstado().get(0).porcentaje());
        assertEquals("NEEDS_ASSISTANCE", p.porEstado().get(1).clave());
        assertEquals(33.3, p.porEstado().get(1).porcentaje());
        assertEquals(1, p.puntosEncuentro().size());
        assertEquals("p1", p.puntosEncuentro().get(0).clave());
        assertEquals(100.0, p.puntosEncuentro().get(0).porcentaje());
        assertEquals(1, p.solicitaronAsistencia());
        assertEquals(33.3, p.porcentajeAsistencia());
    }

    @Test
    void computesIncidentsByPriority() {
        EventIndicatorsResponse.Incidents i = compute(drill()).incidentes();

        assertEquals(3, i.total());
        assertEquals("ALTA", i.porCategoria().get(0).clave());
        assertEquals(2, i.porCategoria().get(0).cantidad());
        assertEquals(66.7, i.porCategoria().get(0).porcentaje());
        assertEquals("BAJA", i.porCategoria().get(1).clave());
    }

    @Test
    void computesAverageTimesFromTheRealStart() {
        EventIndicatorsResponse response = compute(drill());
        EventIndicatorsResponse.ResponseTimes t = response.tiempos();

        assertEquals(START, response.inicioEvento());
        // First check-in per user: 30, 60, 120 seconds.
        assertEquals(70.0, t.hastaCheckin().promedioSegundos());
        assertEquals(3, t.hastaCheckin().muestras());
        // First SAFE per user: u1 90, u2 60.
        assertEquals(75.0, t.evacuacion().promedioSegundos());
        assertEquals(2, t.evacuacion().muestras());
        // Incident A 60s, incident B 300s; C was never attended.
        assertEquals(180.0, t.hastaPrimeraAtencion().promedioSegundos());
        assertEquals(2, t.hastaPrimeraAtencion().muestras());
    }

    @Test
    void ranksTheSlowestLocationsFirst() {
        List<EventIndicatorsResponse.SlowLocation> slow = compute(drill()).tiempos().ubicacionesMasLentas();

        assertEquals(2, slow.size());
        assertEquals("ZONA", slow.get(0).ubicacionTipo());
        assertEquals("z1", slow.get(0).ubicacionId());
        assertEquals(300.0, slow.get(0).promedioSegundos());
        assertEquals("EDIFICIO", slow.get(1).ubicacionTipo());
        assertEquals(60.0, slow.get(1).promedioSegundos());
    }

    @Test
    void withoutFactsEverythingIsZeroOrNull() {
        EventIndicatorsResponse response = compute(List.of());

        assertNull(response.inicioEvento());
        assertEquals(0, response.notificacion().destinatarios());
        assertNull(response.notificacion().porcentajeConfirmacion());
        assertEquals(0, response.participacion().participantes());
        assertNull(response.participacion().porcentajeParticipacion());
        assertEquals(0, response.incidentes().total());
        assertNull(response.tiempos().hastaCheckin().promedioSegundos());
        assertEquals(0, response.tiempos().hastaPrimeraAtencion().muestras());
        assertEquals(0, response.tiempos().ubicacionesMasLentas().size());
    }

    @Test
    void withoutStartTheTimesFromTheStartAreEmptyButTheRestIsComputed() {
        List<AuditEvent> facts = drill().stream().filter(f -> !"EVENT_STARTED".equals(f.accion())).toList();

        EventIndicatorsResponse response = compute(facts);

        assertNull(response.inicioEvento());
        assertNull(response.tiempos().hastaCheckin().promedioSegundos());
        assertNull(response.tiempos().evacuacion().promedioSegundos());
        assertEquals(3, response.participacion().participantes());
        assertEquals(180.0, response.tiempos().hastaPrimeraAtencion().promedioSegundos());
    }

    @Test
    void aCheckinBeforeTheStartIsNotAnArrivalTime() {
        List<AuditEvent> facts = List.of(
                ok("MS06", "EVENT_STARTED", null, "evento:" + EVENT, 0, null),
                ok("MS04", "REGISTRO_CHECKIN", "u1", "checkin:c1", -30, Map.of("estado", "SAFE")),
                ok("MS04", "REGISTRO_CHECKIN", "u2", "checkin:c2", 40, Map.of("estado", "SAFE")));

        EventIndicatorsResponse response = compute(facts);

        assertEquals(2, response.participacion().participantes());
        assertEquals(1, response.tiempos().hastaCheckin().muestras());
        assertEquals(40.0, response.tiempos().hastaCheckin().promedioSegundos());
    }

    @Test
    void anActionFromAnotherServiceIsIgnored() {
        List<AuditEvent> facts = List.of(
                ok("MS04", "CONFIRM", "u1", "alerta:a1", 10, null),
                ok("MS01", "REGISTRO_CHECKIN", "u1", "checkin:c1", 10, Map.of("estado", "SAFE")),
                ok("MS03", "NOTIFICATION_SENT", null, "alerta:a1", 5, Map.of("destinatarios", 2)));

        EventIndicatorsResponse response = compute(facts);

        assertEquals(0, response.notificacion().confirmados());
        assertEquals(0, response.participacion().participantes());
        assertEquals(2, response.notificacion().destinatarios());
    }

    @Test
    void missingAttributesGoToDefaultGroups() {
        List<AuditEvent> facts = List.of(
                ok("MS04", "REGISTRO_CHECKIN", "u1", "checkin:c1", 10, null),
                ok("MS05", "CREAR_INCIDENTE", "u1", "incidente:a", 10, null));

        EventIndicatorsResponse response = compute(facts);

        assertEquals("SIN_ESTADO", response.participacion().porEstado().get(0).clave());
        assertEquals("SIN_PRIORIDAD", response.incidentes().porCategoria().get(0).clave());
    }

    @Test
    void keepsOnlyTheTenSlowestLocations() {
        List<AuditEvent> facts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String incident = "incidente:" + i;
            facts.add(ok("MS05", "CREAR_INCIDENTE", "u1", incident, 0,
                    Map.of("prioridad", "ALTA", "ubicacion_tipo", "ZONA", "ubicacion_id", "z" + i)));
            facts.add(ok("MS05", "INICIAR_ATENCION", "br", incident, 10 + i, null));
        }

        List<EventIndicatorsResponse.SlowLocation> slow = compute(facts).tiempos().ubicacionesMasLentas();

        assertEquals(EventIndicatorCalculator.MAX_SLOW_LOCATIONS, slow.size());
        assertEquals("z11", slow.get(0).ubicacionId());
    }
}
