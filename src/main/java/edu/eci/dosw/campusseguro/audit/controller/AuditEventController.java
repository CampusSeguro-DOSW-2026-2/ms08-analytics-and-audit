package edu.eci.dosw.campusseguro.audit.controller;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.service.AuditIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal route: other microservices send their audit facts here (through the Gateway). */
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditEventController {

    private final AuditIngestionService ingestionService;

    public AuditEventController(AuditIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Answers 202 without body, also when the fact was already stored. */
    @PostMapping
    public ResponseEntity<Void> register(@Valid @RequestBody AuditEventRequest request, Authentication caller) {
        ingestionService.register(request, caller.getName());
        return ResponseEntity.accepted().build();
    }
}
