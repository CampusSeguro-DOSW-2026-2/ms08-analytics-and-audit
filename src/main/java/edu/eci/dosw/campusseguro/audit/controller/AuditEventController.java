package edu.eci.dosw.campusseguro.audit.controller;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.dto.AuditEventResponse;
import edu.eci.dosw.campusseguro.audit.dto.AuditQuery;
import edu.eci.dosw.campusseguro.audit.dto.PageResponse;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.security.AuditVisibility;
import edu.eci.dosw.campusseguro.audit.service.AuditIngestionService;
import edu.eci.dosw.campusseguro.audit.service.AuditQueryService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit records. POST is an internal route (other microservices send their facts through the
 * Gateway); GET is for users with the audit read permissions.
 */
@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditEventController {

    private final AuditIngestionService ingestionService;
    private final AuditQueryService queryService;

    public AuditEventController(AuditIngestionService ingestionService, AuditQueryService queryService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }

    /** Answers 202 without body, also when the fact was already stored. */
    @PostMapping
    public ResponseEntity<Void> register(@Valid @RequestBody AuditEventRequest request, Authentication caller) {
        ingestionService.register(request, caller.getName());
        return ResponseEntity.accepted().build();
    }

    /** Filtered, paged list (newest first) of the records the user may read. */
    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(name = "usuario_id", required = false) String usuarioId,
            @RequestParam(name = "evento_id", required = false) String eventoId,
            @RequestParam(name = "tipo_evento", required = false) EventKind tipoEvento,
            @RequestParam(name = "recurso_afectado", required = false) String recursoAfectado,
            @RequestParam(name = "correlation_id", required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication caller) {
        AuditQuery query = new AuditQuery(
                modulo, accion, usuarioId, eventoId, tipoEvento, recursoAfectado, correlationId,
                desde == null ? null : desde.toInstant(),
                hasta == null ? null : hasta.toInstant(),
                page, size);
        return queryService.search(query, AuditVisibility.of(caller.getAuthorities()));
    }

    /** One record, if the user may read its kind. */
    @GetMapping("/{id}")
    public AuditEventResponse get(@PathVariable String id, Authentication caller) {
        return queryService.get(id, AuditVisibility.of(caller.getAuthorities()));
    }
}
