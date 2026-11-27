package edu.eci.dosw.campusseguro.audit.dto;

import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Instant;

/**
 * Filters of the audit search. Every filter is optional; {@code desde} and {@code hasta} are
 * both included.
 */
public record AuditQuery(
        String modulo,
        String accion,
        String usuarioId,
        String eventoId,
        EventKind tipoEvento,
        String recursoAfectado,
        String correlationId,
        Instant desde,
        Instant hasta,
        Integer page,
        Integer size) {
}
