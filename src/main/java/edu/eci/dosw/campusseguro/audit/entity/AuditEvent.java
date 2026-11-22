package edu.eci.dosw.campusseguro.audit.entity;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One audited fact. Records are only inserted, never changed (append-only).
 *
 * @param id             event id (sent by the service, or a hash when it did not send one)
 * @param usuarioId      user that did the action; null for system actions
 * @param modulo         service that sent the fact (MS01..MS08)
 * @param accion         action code in upper snake case
 * @param recursoAfectado affected resource as {@code <type>:<id>}, or null
 * @param resultado      EXITO or FALLO
 * @param fechaHora      when the action happened (UTC); the TTL index uses it
 * @param recibidoEn     when MS08 received it (UTC)
 * @param correlationId  correlation id of the original request
 * @param detalle        generic code, never personal data
 * @param eventoId       event (drill or real) the fact belongs to, or null
 * @param tipoEvento     SIMULACRO or REAL, or null
 * @param atributos      flat map of ids, codes and counts used by the indicators
 * @param servicioOrigen service name injected by the Gateway ({@code X-Service-Name})
 */
@Document(collection = "audit_events")
public record AuditEvent(
        @Id String id,
        @Field("usuario_id") String usuarioId,
        String modulo,
        String accion,
        @Field("recurso_afectado") String recursoAfectado,
        AuditResult resultado,
        @Field("fecha_hora") Instant fechaHora,
        @Field("recibido_en") Instant recibidoEn,
        @Field("correlation_id") String correlationId,
        String detalle,
        @Field("evento_id") String eventoId,
        @Field("tipo_evento") EventKind tipoEvento,
        Map<String, Object> atributos,
        @Field("servicio_origen") String servicioOrigen) {
}
