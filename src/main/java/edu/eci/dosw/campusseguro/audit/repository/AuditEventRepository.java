package edu.eci.dosw.campusseguro.audit.repository;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Access to the {@code audit_events} collection. Use {@code insert}, never {@code save} (append-only). */
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
}
