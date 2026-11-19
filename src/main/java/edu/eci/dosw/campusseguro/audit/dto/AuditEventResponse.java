package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Instant;
import java.util.Map;

/** One audit record in an answer (same fields as the request plus {@code recibido_en}). */
public record AuditEventResponse(
        String id,
        @JsonProperty("usuario_id") String usuarioId,
        String modulo,
        String accion,
        @JsonProperty("recurso_afectado") String recursoAfectado,
        AuditResult resultado,
        @JsonProperty("fecha_hora") Instant fechaHora,
        @JsonProperty("recibido_en") Instant recibidoEn,
        @JsonProperty("correlation_id") String correlationId,
        String detalle,
        @JsonProperty("evento_id") String eventoId,
        @JsonProperty("tipo_evento") EventKind tipoEvento,
        Map<String, Object> atributos) {
}
