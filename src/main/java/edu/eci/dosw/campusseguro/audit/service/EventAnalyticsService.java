package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.util.List;

/** Analytics over several events: comparison between events and breakdown by building or zone. */
public interface EventAnalyticsService {

    /**
     * Compares 2 to 10 events of the same kind.
     *
     * @throws edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException 400 for a bad id, a wrong
     *         number of events or events of different kinds
     * @throws edu.eci.dosw.campusseguro.audit.exception.BusinessException 404 if an event has no facts,
     *         403 if the user may not read the indicators of the kind
     */
    EventComparisonResponse compare(List<String> eventIds, IndicatorVisibility visibility);

    /** Breakdown by building or zone of 1 to 10 events of the same kind (same errors as {@link #compare}). */
    LocationAnalyticsResponse locations(List<String> eventIds, IndicatorVisibility visibility);
}
