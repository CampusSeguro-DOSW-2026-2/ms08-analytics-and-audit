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

/** Comparison and location endpoints against a real MongoDB. */
class EventAnalyticsIT extends MongoIntegrationTest {

    private static final String COMPARISONS = "/api/v1/analytics/comparisons";
    private static final String LOCATIONS = "/api/v1/analytics/locations";
    private static final String USER = "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b";
    private static final String D1 = "11111111-1111-4111-8111-111111111111";
    private static final String D2 = "22222222-2222-4222-8222-222222222222";
    private static final String R1 = "33333333-3333-4333-8333-333333333333";
    private static final String DRILL = "ms08:indicators_drill:read";
    private static final String REAL = "ms08:indicators_real_event:read";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditEventRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        // Relative to "now" so the 6-month TTL never deletes the facts during the test.
        Instant now = Instant.now();
        drill(D1, EventKind.SIMULACRO, now.minusSeconds(7200), 300);
        drill(D2, EventKind.SIMULACRO, now.minusSeconds(3600), 120);
        drill(R1, EventKind.REAL, now.minusSeconds(1800), 60);
    }

    private void drill(String id, EventKind kind, Instant start, long attention) {
        insert(id + "-s", "MS06", "EVENT_STARTED", null, "evento:" + id, id, kind, start, null);
        insert(id + "-n", "MS03", "NOTIFICATION_SENT", null, "alerta:" + id, id, kind, start.plusSeconds(1),
                Map.of("destinatarios", 10, "enviados", 10, "fallidos", 0));
        insert(id + "-c", "MS04", "REGISTRO_CHECKIN", USER, "checkin:" + id, id, kind, start.plusSeconds(40),
                Map.of("estado", "SAFE", "punto_encuentro_id", "p1"));
        insert(id + "-i", "MS05", "CREAR_INCIDENTE", USER, "incidente:" + id, id, kind, start.plusSeconds(100),
                Map.of("prioridad", "ALTA", "ubicacion_tipo", "EDIFICIO", "ubicacion_id", "b1"));
        insert(id + "-a", "MS05", "INICIAR_ATENCION", "br1", "incidente:" + id, id, kind,
                start.plusSeconds(100 + attention), null);
    }

    private void insert(String id, String module, String action, String user, String resource, String eventId,
            EventKind kind, Instant time, Map<String, Object> attributes) {
        repository.insert(new AuditEvent(id, user, module, action, resource, AuditResult.EXITO, time,
                time.plusSeconds(1), "corr-" + id, null, eventId, kind, attributes, "ms00"));
    }

    private MockHttpServletRequestBuilder as(String permissions, String path) {
        return get(path).header("X-User-Id", USER).header("X-User-Permission", permissions);
    }

    @Test
    void comparesTwoDrillsOldestFirst() throws Exception {
        mockMvc.perform(as(DRILL, COMPARISONS).param("evento_id", D2).param("evento_id", D1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(2))
                .andExpect(jsonPath("$.eventos[0].evento_id").value(D1))
                .andExpect(jsonPath("$.eventos[0].cambio_vs_anterior").doesNotExist())
                .andExpect(jsonPath("$.eventos[0].hasta_primera_atencion_segundos").value(300.0))
                .andExpect(jsonPath("$.eventos[1].evento_id").value(D2))
                .andExpect(jsonPath("$.eventos[1].cambio_vs_anterior.hasta_primera_atencion_segundos").value(-180.0));
    }

    @Test
    void theIdsCanComeSeparatedByCommas() throws Exception {
        mockMvc.perform(as(DRILL, COMPARISONS).param("evento_id", D1 + "," + D2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(2));
    }

    @Test
    void comparisonErrors() throws Exception {
        mockMvc.perform(as(DRILL, COMPARISONS).param("evento_id", D1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(as(DRILL, COMPARISONS)).andExpect(status().isBadRequest());
        mockMvc.perform(as(DRILL, COMPARISONS).param("evento_id", D1).param("evento_id", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(DRILL + "," + REAL, COMPARISONS).param("evento_id", D1).param("evento_id", R1))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(DRILL, COMPARISONS).param("evento_id", D1)
                        .param("evento_id", "99999999-9999-4999-8999-999999999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(REAL, COMPARISONS).param("evento_id", D1).param("evento_id", D2))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(COMPARISONS).param("evento_id", D1).param("evento_id", D2))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as("ms08:audit_drill:read", COMPARISONS).param("evento_id", D1).param("evento_id", D2))
                .andExpect(status().isForbidden());
    }

    @Test
    void locationsAddUpTheEvents() throws Exception {
        mockMvc.perform(as(DRILL, LOCATIONS).param("evento_id", D1).param("evento_id", D2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicaciones.length()").value(1))
                .andExpect(jsonPath("$.ubicaciones[0].ubicacion_tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.ubicaciones[0].ubicacion_id").value("b1"))
                .andExpect(jsonPath("$.ubicaciones[0].incidentes").value(2))
                .andExpect(jsonPath("$.ubicaciones[0].hasta_primera_atencion.promedio_segundos").value(210.0))
                .andExpect(jsonPath("$.ubicaciones[0].por_evento.length()").value(2));
    }

    @Test
    void locationsOfARealEventNeedTheRealEventPermission() throws Exception {
        mockMvc.perform(as(REAL, LOCATIONS).param("evento_id", R1)).andExpect(status().isOk());
        mockMvc.perform(as(DRILL, LOCATIONS).param("evento_id", R1)).andExpect(status().isForbidden());
        mockMvc.perform(as(DRILL, LOCATIONS)).andExpect(status().isBadRequest());
    }
}
