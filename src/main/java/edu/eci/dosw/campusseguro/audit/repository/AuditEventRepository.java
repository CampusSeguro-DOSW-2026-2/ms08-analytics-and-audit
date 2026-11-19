package edu.eci.dosw.campusseguro.audit.repository;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Access to the {@code audit_events} collection. Use {@code insert}, never {@code save} (append-only). */
public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    /** Any fact of the event; used to know the kind of the event. */
    Optional<AuditEvent> findFirstByEventoId(String eventoId);

    /** Facts of the event with one of the given actions (uses the evento_id + fecha_hora index). */
    List<AuditEvent> findByEventoIdAndAccionIn(String eventoId, Collection<String> acciones);
}
