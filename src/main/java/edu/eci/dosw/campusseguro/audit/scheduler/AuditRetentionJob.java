package edu.eci.dosw.campusseguro.audit.scheduler;

import edu.eci.dosw.campusseguro.audit.config.AuditProperties;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily delete of audit records older than the retention period. It is only a fallback for a
 * database without TTL index support: enable it with {@code app.audit.cleanup-job-enabled=true}
 * and turn the TTL index off with {@code app.audit.ttl-index-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.audit", name = "cleanup-job-enabled", havingValue = "true")
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final MongoTemplate mongoTemplate;
    private final AuditProperties properties;
    private final Clock clock;

    public AuditRetentionJob(MongoTemplate mongoTemplate, AuditProperties properties, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.audit.cleanup-cron:0 30 3 * * *}", zone = "${app.timezone:America/Bogota}")
    public void deleteExpired() {
        long deleted = deleteOlderThanRetention();
        log.info("Audit retention job deleted {} records", deleted);
    }

    /** Deletes the records whose {@code fecha_hora} is older than the retention period. */
    public long deleteOlderThanRetention() {
        Instant limit = clock.instant().minus(Duration.ofDays(properties.retentionDays()));
        return mongoTemplate.remove(Query.query(Criteria.where("fecha_hora").lt(limit)), AuditEvent.class)
                .getDeletedCount();
    }
}
