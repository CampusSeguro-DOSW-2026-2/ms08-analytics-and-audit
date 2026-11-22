package edu.eci.dosw.campusseguro.audit.repository;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** Dynamic search over {@code audit_events} (the filters are optional, so a fixed query is not enough). */
@Repository
public class AuditEventSearchRepository {

    private final MongoTemplate mongoTemplate;

    public AuditEventSearchRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Runs the query and returns the requested page with the total count. */
    public Page<AuditEvent> search(Query query, Pageable pageable) {
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), AuditEvent.class);
        List<AuditEvent> items = mongoTemplate.find(Query.of(query).with(pageable), AuditEvent.class);
        return new PageImpl<>(items, pageable, total);
    }
}
