package edu.eci.dosw.campusseguro.audit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base class of the integration tests: starts one real MongoDB in Docker (shared by all the
 * classes of the run) and points the application to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class MongoIntegrationTest {

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/audit_test");
    }
}
