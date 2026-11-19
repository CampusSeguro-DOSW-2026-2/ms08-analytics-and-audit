package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.service.EventIndicatorService;
import edu.eci.dosw.campusseguro.audit.service.EventFactsLoader;
import edu.eci.dosw.campusseguro.audit.service.EventIndicatorCalculator;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Computes the indicators of one event on demand, so late facts are always included. */
@Service
public class EventIndicatorServiceImpl implements EventIndicatorService {

    private final EventFactsLoader loader;
    private final EventIndicatorCalculator calculator;
    private final Clock clock;

    public EventIndicatorServiceImpl(EventFactsLoader loader, EventIndicatorCalculator calculator, Clock clock) {
        this.loader = loader;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    public EventIndicatorsResponse forEvent(String eventId, IndicatorVisibility visibility) {
        EventFactsLoader.EventFacts event = loader.load(eventId, visibility);
        return calculator.compute(event.eventId(), event.kind(), event.facts(), clock.instant());
    }
}
