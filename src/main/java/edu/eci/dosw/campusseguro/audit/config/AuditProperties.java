package edu.eci.dosw.campusseguro.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code app.audit}.
 *
 * @param retentionDays     days an audit record is kept (180 = 6 months)
 * @param ttlIndexEnabled   create the TTL index so MongoDB deletes old records by itself
 * @param cleanupJobEnabled run a daily delete instead (use it if the database has no TTL support)
 */
@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(int retentionDays, boolean ttlIndexEnabled, boolean cleanupJobEnabled) {
}
