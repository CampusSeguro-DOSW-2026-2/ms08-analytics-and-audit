package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.BusinessException;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Loads the facts the indicators need for one event, after checking the id and the permission of the
 * user for the kind of the event. The kind of the event is the one its facts carry.
 */
@Component
public class EventFactsLoader {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AuditEventRepository repository;

    public EventFactsLoader(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Facts of one event and its kind. */
    public record EventFacts(String eventId, EventKind kind, List<AuditEvent> facts) {
    }

    /** 403 when the user has none of the indicator permissions. */
    public void requireAnyPermission(IndicatorVisibility visibility) {
        if (!visibility.any()) {
            throw forbidden();
        }
    }

    /** Checks that the id is a UUID and returns it in lowercase (the way ids are stored). */
    public String normalize(String eventId) {
        if (eventId == null || !UUID.matcher(eventId).matches()) {
            throw new InvalidFieldException("evento_id", "Debe ser un UUID");
        }
        return eventId.toLowerCase();
    }

    /**
     * Loads the event.
     *
     * @throws InvalidFieldException 400 when the id is not a UUID
     * @throws BusinessException     404 when MS08 has no facts for the event; 403 when the user may not
     *                               read the indicators of its kind
     */
    public EventFacts load(String eventId, IndicatorVisibility visibility) {
        requireAnyPermission(visibility);
        String id = normalize(eventId);
        AuditEvent any = repository.findFirstByEventoId(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage()));
        if (any.tipoEvento() == null || !visibility.canRead(any.tipoEvento())) {
            throw forbidden();
        }
        return new EventFacts(id, any.tipoEvento(),
                repository.findByEventoIdAndAccionIn(id, EventIndicatorCalculator.ACTIONS));
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }
}
