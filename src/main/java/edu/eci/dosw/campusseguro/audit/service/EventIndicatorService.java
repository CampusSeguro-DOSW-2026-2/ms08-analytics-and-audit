package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;

/** Indicators of one event. */
public interface EventIndicatorService {

    /**
     * Computes the indicators of an event, if the user may read the indicators of its kind.
     *
     * @param eventId    event id (UUID)
     * @param visibility indicator permissions of the user
     * @throws edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException if the id is not a UUID
     * @throws edu.eci.dosw.campusseguro.audit.exception.BusinessException 404 when MS08 has no facts for
     *         the event, 403 when the user may not read that kind
     */
    EventIndicatorsResponse forEvent(String eventId, IndicatorVisibility visibility);
}
