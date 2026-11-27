package edu.eci.dosw.campusseguro.audit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.audit.MongoIntegrationTest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ingestion endpoint against a real MongoDB. */
class AuditEventIT extends MongoIntegrationTest {

    private static final String URL = "/api/v1/audit/events";
    private static final String ID = "7d444840-9dc0-41d8-8d1c-2d5c6b9a4f10";
    private static final String EVENT = "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditEventRepository repository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private String body(String id, String extra) {
        return "{" + (id == null ? "" : "\"id\":\"" + id + "\",")
                + "\"usuario_id\":\"3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b\","
                + "\"modulo\":\"MS06\",\"accion\":\"EVENT_CREATED\",\"recurso_afectado\":\"evento:" + EVENT + "\","
                + "\"resultado\":\"EXITO\",\"fecha_hora\":\"" + Instant.now() + "\","
                + "\"correlation_id\":\"corr-1\",\"evento_id\":\"" + EVENT + "\",\"tipo_evento\":\"SIMULACRO\""
                + extra + "}";
    }

    private MockHttpServletRequestBuilder service(String json) {
        return post(URL).header("X-Service-Name", "ms06").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void storesTheFactAndAnswers202WithoutBody() throws Exception {
        mockMvc.perform(service(body(ID, ",\"atributos\":{\"participantes_esperados\":120,\"es_oficial\":true}")))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        AuditEvent stored = repository.findById(ID).orElseThrow();
        assertEquals("MS06", stored.modulo());
        assertEquals("ms06", stored.servicioOrigen());
        assertEquals(EventKind.SIMULACRO, stored.tipoEvento());
        assertEquals(EVENT, stored.eventoId());
        assertEquals(120, ((Number) stored.atributos().get("participantes_esperados")).intValue());
        assertTrue(stored.recibidoEn() != null);
    }

    @Test
    void sendingTheSameIdAgainIsAcceptedAndStoredOnce() throws Exception {
        mockMvc.perform(service(body(ID, ""))).andExpect(status().isAccepted());
        mockMvc.perform(service(body(ID, ""))).andExpect(status().isAccepted());

        assertEquals(1, repository.count());
    }

    @Test
    void withoutIdTheSameFactIsStoredOnce() throws Exception {
        String json = body(null, "");
        mockMvc.perform(service(json)).andExpect(status().isAccepted());
        mockMvc.perform(service(json)).andExpect(status().isAccepted());

        assertEquals(1, repository.count());
        assertEquals(64, repository.findAll().get(0).id().length());
    }

    @Test
    void anInvalidBodyGets400WithTheFieldDetail() throws Exception {
        mockMvc.perform(service(body(ID, "").replace("\"MS06\"", "\"XX99\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("modulo"));
        assertEquals(0, repository.count());
    }

    @Test
    void anUnknownTopLevelFieldGets400() throws Exception {
        mockMvc.perform(service(body(ID, ",\"extra\":\"x\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void aBrokenContractRuleGets400() throws Exception {
        mockMvc.perform(service(body(ID, "").replace(",\"tipo_evento\":\"SIMULACRO\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("tipo_evento"));
        mockMvc.perform(service(body(ID, ",\"atributos\":{\"a\":{\"b\":1}}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("atributos.a"));
    }

    @Test
    void aUserCannotUseTheInternalRoute() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-User-Id", "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b")
                        .header("X-User-Permission", "ms08:audit_drill:read")
                        .contentType(MediaType.APPLICATION_JSON).content(body(ID, "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void aRequestWithoutIdentityGets401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ID, "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theIndexesExistAndTheTtlIsSixMonths() {
        List<org.springframework.data.mongodb.core.index.IndexInfo> indexes =
                mongoTemplate.indexOps(AuditEvent.class).getIndexInfo();

        var ttl = indexes.stream().filter(index -> "ttl_fecha_hora".equals(index.getName())).findFirst()
                .orElseThrow();
        assertEquals(Duration.ofDays(180), ttl.getExpireAfter().orElseThrow());
        for (String name : List.of("tipo_fecha", "evento_fecha", "usuario_fecha", "modulo_accion_fecha", "correlation")) {
            assertTrue(indexes.stream().anyMatch(index -> name.equals(index.getName())), name);
        }
    }
}
