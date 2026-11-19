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

/** Query endpoints against a real MongoDB: visibility by permission, filters, paging and detail. */
class AuditEventQueryIT extends MongoIntegrationTest {

    private static final String URL = "/api/v1/audit/events";
    private static final String USER = "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b";
    private static final String DRILL_EVENT = "11111111-1111-4111-8111-111111111111";
    private static final String REAL_EVENT = "22222222-2222-4222-8222-222222222222";
    private static final String DRILL = "ms08:audit_drill:read";
    private static final String REAL = "ms08:audit_real_event:read";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditEventRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        // Instants are relative to "now" so the 6-month TTL never deletes them during the test.
        Instant base = Instant.now().minusSeconds(3600);
        repository.insert(event("drill-1", "MS06", "EVENT_CREATED", DRILL_EVENT, EventKind.SIMULACRO, base));
        repository.insert(event("drill-2", "MS03", "CONFIRM", DRILL_EVENT, EventKind.SIMULACRO, base.plusSeconds(60)));
        repository.insert(event("real-1", "MS03", "CREATE", REAL_EVENT, EventKind.REAL, base.plusSeconds(120)));
        repository.insert(event("admin-1", "MS01", "LOGIN", null, null, base.plusSeconds(180)));
    }

    private AuditEvent event(String id, String module, String action, String eventId, EventKind kind, Instant time) {
        return new AuditEvent(id, USER, module, action, null, AuditResult.EXITO, time, time.plusSeconds(1),
                "corr-" + id, null, eventId, kind, Map.of("destinatarios", 10), "ms00");
    }

    private MockHttpServletRequestBuilder as(String permissions, String path) {
        return get(path).header("X-User-Id", USER).header("X-User-Permission", permissions);
    }

    @Test
    void drillPermissionSeesOnlyDrills() throws Exception {
        mockMvc.perform(as(DRILL, URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andExpect(jsonPath("$.contenido[*].tipo_evento").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("SIMULACRO"))));
    }

    @Test
    void realEventPermissionSeesOnlyRealEvents() throws Exception {
        mockMvc.perform(as(REAL, URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value("real-1"));
    }

    @Test
    void bothPermissionsSeeEverythingNewestFirst() throws Exception {
        mockMvc.perform(as(DRILL + "," + REAL, URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(4))
                .andExpect(jsonPath("$.total_paginas").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.contenido[0].id").value("admin-1"))
                .andExpect(jsonPath("$.contenido[3].id").value("drill-1"))
                .andExpect(jsonPath("$.contenido[0].servicio_origen").doesNotExist());
    }

    @Test
    void aUserWithoutAuditPermissionGets403() throws Exception {
        mockMvc.perform(as("ms08:indicators_drill:read", URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void aServiceCannotReadTheAudit() throws Exception {
        mockMvc.perform(get(URL).header("X-Service-Name", "ms06"))
                .andExpect(status().isForbidden());
    }

    @Test
    void asksForAKindThatIsNotAllowedGets403() throws Exception {
        mockMvc.perform(as(DRILL, URL).param("tipo_evento", "REAL"))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtersByModuleActionEventAndCorrelation() throws Exception {
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("modulo", "MS03"))
                .andExpect(jsonPath("$.total_elementos").value(2));
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("accion", "LOGIN"))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value("admin-1"));
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("evento_id", DRILL_EVENT.toUpperCase()))
                .andExpect(jsonPath("$.total_elementos").value(2));
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("correlation_id", "corr-real-1"))
                .andExpect(jsonPath("$.total_elementos").value(1));
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("usuario_id", USER))
                .andExpect(jsonPath("$.total_elementos").value(4));
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("tipo_evento", "REAL"))
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    @Test
    void filtersByDateRange() throws Exception {
        Instant from = Instant.now().minusSeconds(3600 - 30);
        Instant to = Instant.now().minusSeconds(3600 - 150);

        mockMvc.perform(as(DRILL + "," + REAL, URL).param("desde", from.toString()).param("hasta", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(2));
    }

    @Test
    void pagesTheResults() throws Exception {
        mockMvc.perform(as(DRILL + "," + REAL, URL).param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.total_elementos").value(4))
                .andExpect(jsonPath("$.total_paginas").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3));
    }

    @Test
    void invalidFiltersGet400WithTheField() throws Exception {
        mockMvc.perform(as(DRILL, URL).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("size"));
        mockMvc.perform(as(DRILL, URL).param("modulo", "XX"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(DRILL, URL).param("tipo_evento", "OTRO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(DRILL, URL).param("desde", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailFollowsTheSameVisibilityRules() throws Exception {
        mockMvc.perform(as(DRILL, URL + "/drill-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modulo").value("MS06"))
                .andExpect(jsonPath("$.atributos.destinatarios").value(10));
        mockMvc.perform(as(DRILL, URL + "/real-1")).andExpect(status().isForbidden());
        mockMvc.perform(as(DRILL, URL + "/admin-1")).andExpect(status().isForbidden());
        mockMvc.perform(as(DRILL + "," + REAL, URL + "/admin-1")).andExpect(status().isOk());
        mockMvc.perform(as(DRILL, URL + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
