package edu.eci.dosw.campusseguro.audit.controller;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import edu.eci.dosw.campusseguro.audit.service.EventIndicatorService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Indicators per event. The permission needed depends on the kind of the event (drill or real). */
@RestController
@RequestMapping("/api/v1/audit/indicators")
public class EventIndicatorController {

    private final EventIndicatorService service;

    public EventIndicatorController(EventIndicatorService service) {
        this.service = service;
    }

    /** Indicators of one event. */
    @GetMapping("/events/{eventoId}")
    public EventIndicatorsResponse forEvent(@PathVariable String eventoId, Authentication caller) {
        return service.forEvent(eventoId, IndicatorVisibility.of(caller.getAuthorities()));
    }
}
