package edu.eci.dosw.campusseguro.audit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.audit.MongoIntegrationTest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Indicators endpoint against a real MongoDB: permission by event kind, numbers and errors. */
class EventIndicatorIT extends MongoIntegrationTest {

    private static final String BASE = "/api/v1/analytics/events/";
    private static final String USER = "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b";
    private static final String DRILL_EVENT = "11111111-1111-4111-8111-111111111111";
    private static final String REAL_EVENT = "22222222-2222-4222-8222-222222222222";
    private static final String DRILL = "ms08:indicators_drill:read";
    private static final String REAL = "ms08:indicators_real_event:read";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditEventRepository repository;

    private Instant start;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        // Relative to "now" so the 6-month TTL never deletes the facts during the test.
        start = Instant.now().minusSeconds(3600);
        insert("d1", "MS06", "EVENT_STARTED", null, DRILL_EVENT, EventKind.SIMULACRO, 0, null);
        insert("d2", "MS03", "NOTIFICATION_SENT", null, DRILL_EVENT, EventKind.SIMULACRO, 5,
                Map.of("destinatarios", 4, "enviados", 4, "fallidos", 0));
        insert("d3", "MS03", "CONFIRM", USER, DRILL_EVENT, EventKind.SIMULACRO, 20, null);
        insert("d4", "MS04", "REGISTRO_CHECKIN", USER, DRILL_EVENT, EventKind.SIMULACRO, 60,
                Map.of("estado", "SAFE", "punto_encuentro_id", "p1"));
        insert("d5", "MS05", "CREAR_INCIDENTE", USER, DRILL_EVENT, EventKind.SIMULACRO, 100,
                Map.of("prioridad", "ALTA", "ubicacion_tipo", "ZONA", "ubicacion_id", "z1"));
        repository.insert(new AuditEvent("d6", "br1", "MS05", "INICIAR_ATENCION", "incidente:d5", AuditResult.EXITO,
                start.plusSeconds(190), start.plusSeconds(191), "corr-d6", null, DRILL_EVENT,
                EventKind.SIMULACRO, null, "ms05"));
        insert("r1", "MS06", "EVENT_STARTED", null, REAL_EVENT, EventKind.REAL, 0, null);
    }

    private void insert(String id, String module, String action, String user, String eventId, EventKind kind,
            long seconds, Map<String, Object> attributes) {
        Instant time = start.plusSeconds(seconds);
        String resource = "CREAR_INCIDENTE".equals(action) ? "incidente:" + id : null;
        repository.insert(new AuditEvent(id, user, module, action, resource, AuditResult.EXITO, time,
                time.plusSeconds(1), "corr-" + id, null, eventId, kind, attributes, "ms00"));
    }

    private MockHttpServletRequestBuilder as(String permissions, String path) {
        return get(path).header("X-User-Id", USER).header("X-User-Permission", permissions);
    }

    @Test
    void computesTheIndicatorsOfADrill() throws Exception {
        mockMvc.perform(as(DRILL, BASE + DRILL_EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento_id").value(DRILL_EVENT))
                .andExpect(jsonPath("$.tipo_evento").value("SIMULACRO"))
                .andExpect(jsonPath("$.notificacion.destinatarios").value(4))
                .andExpect(jsonPath("$.notificacion.confirmados").value(1))
                .andExpect(jsonPath("$.notificacion.porcentaje_confirmacion").value(25.0))
                .andExpect(jsonPath("$.participacion.participantes").value(1))
                .andExpect(jsonPath("$.participacion.porcentaje_participacion").value(25.0))
                .andExpect(jsonPath("$.participacion.por_estado[0].clave").value("SAFE"))
                .andExpect(jsonPath("$.participacion.puntos_encuentro[0].clave").value("p1"))
                .andExpect(jsonPath("$.incidentes.total").value(1))
                .andExpect(jsonPath("$.incidentes.por_categoria[0].clave").value("ALTA"))
                .andExpect(jsonPath("$.tiempos.hasta_checkin.promedio_segundos").value(60.0))
                .andExpect(jsonPath("$.tiempos.evacuacion.promedio_segundos").value(60.0))
                .andExpect(jsonPath("$.tiempos.hasta_primera_atencion.promedio_segundos").value(90.0))
                .andExpect(jsonPath("$.tiempos.ubicaciones_mas_lentas[0].ubicacion_id").value("z1"));
    }

    @Test
    void theIdIsAcceptedInUppercase() throws Exception {
        mockMvc.perform(as(DRILL, BASE + DRILL_EVENT.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento_id").value(DRILL_EVENT));
    }

    @Test
    void aDrillNeedsTheDrillPermission() throws Exception {
        mockMvc.perform(as(REAL, BASE + DRILL_EVENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void aRealEventNeedsTheRealEventPermission() throws Exception {
        mockMvc.perform(as(REAL, BASE + REAL_EVENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_evento").value("REAL"));
        mockMvc.perform(as(DRILL, BASE + REAL_EVENT)).andExpect(status().isForbidden());
    }

    @Test
    void anAuditPermissionIsNotEnough() throws Exception {
        mockMvc.perform(as("ms08:audit_drill:read", BASE + DRILL_EVENT)).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownEventGets404() throws Exception {
        mockMvc.perform(as(DRILL + "," + REAL, BASE + "33333333-3333-4333-8333-333333333333"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void anInvalidIdGets400() throws Exception {
        mockMvc.perform(as(DRILL, BASE + "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void withoutIdentityGets401() throws Exception {
        mockMvc.perform(get(BASE + DRILL_EVENT)).andExpect(status().isUnauthorized());
    }
}
