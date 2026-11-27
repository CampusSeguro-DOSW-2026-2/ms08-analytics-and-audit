package edu.eci.dosw.campusseguro.audit.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import edu.eci.dosw.campusseguro.audit.config.AuditProperties;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Checks that the fallback job deletes what is older than the retention period. */
class AuditRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-18T15:00:00Z");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AuditRetentionJob job = new AuditRetentionJob(
            mongoTemplate, new AuditProperties(180, false, true), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void deletesRecordsOlderThanTheRetention() {
        when(mongoTemplate.remove(any(Query.class), eq(AuditEvent.class))).thenReturn(DeleteResult.acknowledged(3));

        assertEquals(3, job.deleteOlderThanRetention());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(query.capture(), eq(AuditEvent.class));
        assertTrue(query.getValue().getQueryObject().containsKey("fecha_hora"));
    }

    @Test
    void theScheduledMethodRuns() {
        when(mongoTemplate.remove(any(Query.class), eq(AuditEvent.class))).thenReturn(DeleteResult.acknowledged(0));

        job.deleteExpired();

        verify(mongoTemplate).remove(any(Query.class), eq(AuditEvent.class));
    }
}
