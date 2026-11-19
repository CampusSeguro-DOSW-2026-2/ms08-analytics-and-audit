package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventResponse;
import edu.eci.dosw.campusseguro.audit.dto.AuditQuery;
import edu.eci.dosw.campusseguro.audit.dto.PageResponse;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.BusinessException;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.mapper.AuditEventMapper;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventSearchRepository;
import edu.eci.dosw.campusseguro.audit.security.AuditVisibility;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Validates the filters, applies the visibility of the user and reads the records. */
@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    private static final int MAX_TEXT = 200;
    private static final Pattern MODULE = Pattern.compile("^MS0[1-8]$");
    private static final Pattern ACTION = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AuditEventSearchRepository searchRepository;
    private final AuditEventRepository repository;
    private final AuditEventMapper mapper;

    public AuditQueryServiceImpl(
            AuditEventSearchRepository searchRepository, AuditEventRepository repository, AuditEventMapper mapper) {
        this.searchRepository = searchRepository;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<AuditEventResponse> search(AuditQuery query, AuditVisibility visibility) {
        requireAnyPermission(visibility);
        validate(query);
        if (query.tipoEvento() != null && !visibility.canRead(query.tipoEvento())) {
            throw forbidden();
        }

        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_SIZE : query.size();
        // Newest first; the id breaks ties so pages do not repeat or skip records.
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaHora", "id"));

        Page<AuditEvent> result = searchRepository.search(buildQuery(query, visibility), pageable);
        return new PageResponse<>(
                result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public AuditEventResponse get(String id, AuditVisibility visibility) {
        requireAnyPermission(visibility);
        AuditEvent event = repository.findById(id.toLowerCase())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.defaultMessage()));
        if (!visibility.canRead(event.tipoEvento())) {
            throw forbidden();
        }
        return mapper.toResponse(event);
    }

    /**
     * Builds the Mongo query. The kind filter is one condition: the one the user asked for (already
     * checked) or, when the user has a single permission, the only kind that user may see.
     */
    private Query buildQuery(AuditQuery filter, AuditVisibility visibility) {
        Query query = new Query();
        EventKind kind = filter.tipoEvento() != null ? filter.tipoEvento() : visibility.onlyKind();
        if (kind != null) {
            query.addCriteria(Criteria.where("tipoEvento").is(kind));
        }
        addIfPresent(query, "modulo", filter.modulo());
        addIfPresent(query, "accion", filter.accion());
        addIfPresent(query, "usuarioId", lower(filter.usuarioId()));
        addIfPresent(query, "eventoId", lower(filter.eventoId()));
        addIfPresent(query, "recursoAfectado", filter.recursoAfectado());
        addIfPresent(query, "correlationId", filter.correlationId());
        if (filter.desde() != null || filter.hasta() != null) {
            Criteria time = Criteria.where("fechaHora");
            if (filter.desde() != null) {
                time = time.gte(filter.desde());
            }
            if (filter.hasta() != null) {
                time = time.lte(filter.hasta());
            }
            query.addCriteria(time);
        }
        return query;
    }

    private void validate(AuditQuery query) {
        if (query.modulo() != null && !MODULE.matcher(query.modulo()).matches()) {
            throw new InvalidFieldException("modulo", "Debe ser MS01..MS08");
        }
        if (query.accion() != null && !ACTION.matcher(query.accion()).matches()) {
            throw new InvalidFieldException("accion", "Debe estar en MAYÚSCULAS con guion bajo");
        }
        requireUuid("usuario_id", query.usuarioId());
        requireUuid("evento_id", query.eventoId());
        requireMaxLength("recurso_afectado", query.recursoAfectado());
        requireMaxLength("correlation_id", query.correlationId());
        if (query.desde() != null && query.hasta() != null && query.desde().isAfter(query.hasta())) {
            throw new InvalidFieldException("desde", "No puede ser posterior a hasta");
        }
        if (query.page() != null && query.page() < 0) {
            throw new InvalidFieldException("page", "Debe ser 0 o mayor");
        }
        if (query.size() != null && (query.size() < 1 || query.size() > MAX_SIZE)) {
            throw new InvalidFieldException("size", "Debe estar entre 1 y 100");
        }
    }

    private void requireUuid(String field, String value) {
        if (value != null && !UUID.matcher(value).matches()) {
            throw new InvalidFieldException(field, "Debe ser un UUID");
        }
    }

    private void requireMaxLength(String field, String value) {
        if (value != null && value.length() > MAX_TEXT) {
            throw new InvalidFieldException(field, "Máximo 200 caracteres");
        }
    }

    private void addIfPresent(Query query, String property, String value) {
        if (value != null && !value.isBlank()) {
            query.addCriteria(Criteria.where(property).is(value));
        }
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private void requireAnyPermission(AuditVisibility visibility) {
        if (!visibility.any()) {
            throw forbidden();
        }
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }
}
