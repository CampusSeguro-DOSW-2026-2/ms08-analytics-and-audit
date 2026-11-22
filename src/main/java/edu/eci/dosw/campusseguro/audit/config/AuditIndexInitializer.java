package edu.eci.dosw.campusseguro.audit.config;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes of {@code audit_events} at startup (there are no migrations). Creating an
 * index that already exists is harmless. The unique {@code _id} gives the idempotency by event id.
 */
@Component
public class AuditIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditIndexInitializer.class);

    static final String TTL_INDEX = "ttl_fecha_hora";

    private final MongoTemplate mongoTemplate;
    private final AuditProperties properties;

    public AuditIndexInitializer(MongoTemplate mongoTemplate, AuditProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexes = mongoTemplate.indexOps(AuditEvent.class);

        if (properties.ttlIndexEnabled()) {
            // Changing retention-days later needs the old TTL index to be dropped first.
            indexes.createIndex(new Index().named(TTL_INDEX)
                    .on("fecha_hora", Direction.ASC)
                    .expire(Duration.ofDays(properties.retentionDays())));
        }
        indexes.createIndex(new Index().named("tipo_fecha")
                .on("tipo_evento", Direction.ASC).on("fecha_hora", Direction.DESC));
        indexes.createIndex(new Index().named("evento_fecha")
                .on("evento_id", Direction.ASC).on("fecha_hora", Direction.DESC));
        indexes.createIndex(new Index().named("usuario_fecha")
                .on("usuario_id", Direction.ASC).on("fecha_hora", Direction.DESC));
        indexes.createIndex(new Index().named("modulo_accion_fecha")
                .on("modulo", Direction.ASC).on("accion", Direction.ASC).on("fecha_hora", Direction.DESC));
        indexes.createIndex(new Index().named("correlation").on("correlation_id", Direction.ASC));
        log.info("Audit indexes ready (ttl={}, retentionDays={})",
                properties.ttlIndexEnabled(), properties.retentionDays());
    }
}
