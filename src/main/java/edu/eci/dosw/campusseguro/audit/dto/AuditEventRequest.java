package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Body of {@code POST /api/v1/audit/events} (API standards, section 12).
 * Unknown top-level fields are rejected with 400 (see {@code spring.jackson.deserialization}
 * in application.yml).
 */
public record AuditEventRequest(
        @Pattern(regexp = AuditEventRequest.UUID, message = "Debe ser un UUID") String id,
        @JsonProperty("usuario_id")
        @Pattern(regexp = AuditEventRequest.UUID, message = "Debe ser un UUID") String usuarioId,
        @NotNull(message = "Es obligatorio")
        @Pattern(regexp = "^MS0[1-8]$", message = "Debe ser MS01..MS08") String modulo,
        @NotNull(message = "Es obligatorio")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Debe estar en MAYÚSCULAS con guion bajo")
        @Size(max = 100, message = "Máximo 100 caracteres") String accion,
        @JsonProperty("recurso_afectado")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*:\\S{1,100}$", message = "Debe tener el formato <tipo>:<id>")
        String recursoAfectado,
        @NotNull(message = "Es obligatorio") AuditResult resultado,
        @JsonProperty("fecha_hora") @NotNull(message = "Es obligatorio") OffsetDateTime fechaHora,
        @JsonProperty("correlation_id")
        @NotBlank(message = "Es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres")
        String correlationId,
        @Size(max = 100, message = "Máximo 100 caracteres") String detalle,
        @JsonProperty("evento_id")
        @Pattern(regexp = AuditEventRequest.UUID, message = "Debe ser un UUID") String eventoId,
        @JsonProperty("tipo_evento") EventKind tipoEvento,
        Map<String, Object> atributos) {

    /** UUID in text form (any case). */
    static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
}
