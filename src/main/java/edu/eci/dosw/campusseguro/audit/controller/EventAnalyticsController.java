package edu.eci.dosw.campusseguro.audit.controller;

import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import edu.eci.dosw.campusseguro.audit.service.EventAnalyticsService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics over several events. The events are sent as {@code evento_id} repeated
 * ({@code ?evento_id=a&evento_id=b}) or separated by commas. The permission needed depends on the kind
 * of the events (drill or real).
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class EventAnalyticsController {

    private final EventAnalyticsService service;

    public EventAnalyticsController(EventAnalyticsService service) {
        this.service = service;
    }

    /** Side-by-side comparison of 2 to 10 events of the same kind. */
    @GetMapping("/comparisons")
    public EventComparisonResponse compare(
            @RequestParam(name = "evento_id", required = false) List<String> eventoIds, Authentication caller) {
        return service.compare(eventoIds, IndicatorVisibility.of(caller.getAuthorities()));
    }

    /** Incidents, assistance requests and response time by building or zone. */
    @GetMapping("/locations")
    public LocationAnalyticsResponse locations(
            @RequestParam(name = "evento_id", required = false) List<String> eventoIds, Authentication caller) {
        return service.locations(eventoIds, IndicatorVisibility.of(caller.getAuthorities()));
    }
}
