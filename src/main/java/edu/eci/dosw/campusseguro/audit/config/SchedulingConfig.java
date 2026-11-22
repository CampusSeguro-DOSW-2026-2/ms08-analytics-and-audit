package edu.eci.dosw.campusseguro.audit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduling only when the retention job is enabled. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.audit", name = "cleanup-job-enabled", havingValue = "true")
public class SchedulingConfig {
}
