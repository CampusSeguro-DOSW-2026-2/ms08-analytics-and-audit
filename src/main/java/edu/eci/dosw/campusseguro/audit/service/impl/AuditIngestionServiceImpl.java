package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.config.AuditProperties;
import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.mapper.AuditEventMapper;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Contract rules of section 12 that annotations cannot express, plus the idempotent insert. */
@Service
public class AuditIngestionServiceImpl implements AuditIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestionServiceImpl.class);

    /** How far in the future {@code fecha_hora} may be (clock differences between services). */
    static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    static final int MAX_ATTRIBUTES = 20;
    static final int MAX_TEXT = 100;
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final AuditEventRepository repository;
    private final AuditEventMapper mapper;
    private final AuditProperties properties;
    private final Clock clock;

    public AuditIngestionServiceImpl(
            AuditEventRepository repository, AuditEventMapper mapper, AuditProperties properties, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void register(AuditEventRequest request, String origin) {
        Instant now = clock.instant();
        validateTime(request, now);
        validateEventKind(request);
        validateAttributes(request.atributos());

        AuditEvent event = mapper.toEntity(request, now, origin);
        try {
            repository.insert(event);
            log.debug("Audit fact stored: id={} module={} action={}", event.id(), event.modulo(), event.accion());
        } catch (DuplicateKeyException duplicate) {
            // Same id again: a retry of a fact already stored. Accepted, nothing changes.
            log.debug("Audit fact already stored, ignored: id={}", event.id());
        }
    }

    /** The time cannot be in the future (> 5 min) nor older than the retention period. */
    private void validateTime(AuditEventRequest request, Instant now) {
        Instant happenedAt = request.fechaHora().toInstant();
        if (happenedAt.isAfter(now.plus(MAX_FUTURE))) {
            throw new InvalidFieldException("fecha_hora", "No puede estar más de 5 minutos en el futuro");
        }
        if (happenedAt.isBefore(now.minus(Duration.ofDays(properties.retentionDays())))) {
            throw new InvalidFieldException("fecha_hora", "Es más antigua que el periodo de retención");
        }
    }

    /** {@code tipo_evento} is required with {@code evento_id}; without it only REAL or null is allowed. */
    private void validateEventKind(AuditEventRequest request) {
        if (request.eventoId() != null && request.tipoEvento() == null) {
            throw new InvalidFieldException("tipo_evento", "Es obligatorio cuando se envía evento_id");
        }
        if (request.eventoId() == null && request.tipoEvento() == EventKind.SIMULACRO) {
            throw new InvalidFieldException("tipo_evento", "SIMULACRO requiere evento_id");
        }
    }

    /** Flat map, at most 20 keys in snake_case; values are text (<= 100), numbers or booleans. */
    private void validateAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw new InvalidFieldException("atributos", "Máximo 20 claves");
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !ATTRIBUTE_KEY.matcher(key).matches()) {
                throw new InvalidFieldException("atributos", "Las claves deben estar en snake_case: " + key);
            }
            boolean validValue = value instanceof Number
                    || value instanceof Boolean
                    || (value instanceof String text && text.length() <= MAX_TEXT);
            if (!validValue) {
                throw new InvalidFieldException("atributos." + key,
                        "Solo texto (máx. 100), número o booleano");
            }
        }
    }
}
