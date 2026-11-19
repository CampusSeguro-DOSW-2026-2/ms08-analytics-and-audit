package edu.eci.dosw.campusseguro.audit.mapper;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Builds the stored record from a request. */
@Component
public class AuditEventMapper {

    /**
     * @param request    validated request
     * @param receivedAt time MS08 received it
     * @param origin     service name sent by the Gateway (for example {@code ms06})
     */
    public AuditEvent toEntity(AuditEventRequest request, Instant receivedAt, String origin) {
        Instant happenedAt = request.fechaHora().toInstant();
        return new AuditEvent(
                idOf(request, happenedAt),
                lower(request.usuarioId()),
                request.modulo(),
                request.accion(),
                request.recursoAfectado(),
                request.resultado(),
                happenedAt,
                receivedAt,
                request.correlationId(),
                request.detalle(),
                lower(request.eventoId()),
                request.tipoEvento(),
                request.atributos() == null ? null : new LinkedHashMap<String, Object>(request.atributos()),
                origin);
    }

    /**
     * The id sent by the service, or a SHA-256 hash of correlation id + module + action + resource +
     * time, so the same fact sent twice gets the same id.
     */
    String idOf(AuditEventRequest request, Instant happenedAt) {
        if (request.id() != null) {
            return request.id().toLowerCase(Locale.ROOT);
        }
        String source = String.join("|",
                request.correlationId(),
                request.modulo(),
                request.accion(),
                String.valueOf(request.recursoAfectado()),
                happenedAt.toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
