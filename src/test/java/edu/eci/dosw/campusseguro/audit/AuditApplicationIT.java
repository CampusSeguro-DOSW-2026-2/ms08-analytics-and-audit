package edu.eci.dosw.campusseguro.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Starts the whole application against a real MongoDB (Docker, via Testcontainers) and checks
 * the base behavior: health, 401 in the standard format and the correlation id.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditApplicationIT {

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/audit_test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void aRequestWithoutIdentityGets401InTheStandardFormat() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events").header("X-Correlation-ID", "abc-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "abc-123"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").value("abc-123"));
    }

    @Test
    void aValidUserPassesTheSecurityLayer() throws Exception {
        // No endpoint exists yet (step 3), so an authenticated request reaches the 404 answer.
        mockMvc.perform(get("/api/v1/audit/events")
                        .header("X-User-Id", "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b")
                        .header("X-User-Permission", "ms08:audit_drill:read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
