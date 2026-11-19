package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.exception.BusinessException;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Checks the permission by the kind of the event and computes the indicators on demand, so late facts
 * are always included. The kind of the event is the one its facts carry.
 */
@Service
public class EventIndicatorServiceImpl implements EventIndicatorService {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AuditEventRepository repository;
    private final EventIndicatorCalculator calculator;
    private final Clock clock;

    public EventIndicatorServiceImpl(
            AuditEventRepository repository, EventIndicatorCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    public EventIndicatorsResponse forEvent(String eventId, IndicatorVisibility visibility) {
        if (!visibility.any()) {
            throw forbidden();
        }
        if (eventId == null || !UUID.matcher(eventId).matches()) {
            throw new InvalidFieldException("evento_id", "Debe ser un UUID");
        }
        String id = eventId.toLowerCase();
        AuditEvent any = repository.findFirstByEventoId(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage()));
        if (any.tipoEvento() == null || !visibility.canRead(any.tipoEvento())) {
            throw forbidden();
        }
        List<AuditEvent> facts = repository.findByEventoIdAndAccionIn(id, EventIndicatorCalculator.ACTIONS);
        return calculator.compute(id, any.tipoEvento(), facts, clock.instant());
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }
}
