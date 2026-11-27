package edu.eci.dosw.campusseguro.audit.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventResponse;
import edu.eci.dosw.campusseguro.audit.dto.AuditQuery;
import edu.eci.dosw.campusseguro.audit.dto.PageResponse;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import edu.eci.dosw.campusseguro.audit.exception.BusinessException;
import edu.eci.dosw.campusseguro.audit.exception.ErrorCode;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.mapper.AuditEventMapper;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.audit.repository.AuditEventSearchRepository;
import edu.eci.dosw.campusseguro.audit.security.AuditVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;

/** Checks the filters, the visibility rules and the paging, with the database mocked. */
class AuditQueryServiceTest {

    private static final AuditVisibility DRILL = new AuditVisibility(true, false);
    private static final AuditVisibility REAL = new AuditVisibility(false, true);
    private static final AuditVisibility BOTH = new AuditVisibility(true, true);
    private static final AuditVisibility NONE = new AuditVisibility(false, false);
    private static final String UUID_TEXT = "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    private final AuditEventSearchRepository searchRepository = mock(AuditEventSearchRepository.class);
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final AuditQueryServiceImpl service =
            new AuditQueryServiceImpl(searchRepository, repository, new AuditEventMapper());

    private AuditQuery query(EventKind kind, Integer page, Integer size) {
        return new AuditQuery(null, null, null, null, kind, null, null, null, null, page, size);
    }

    private AuditEvent event(EventKind kind) {
        return new AuditEvent("id-1", null, "MS06", "EVENT_CREATED", null, AuditResult.EXITO,
                Instant.parse("2026-11-18T15:00:00Z"), Instant.parse("2026-11-18T15:00:01Z"),
                "corr", null, kind == null ? null : UUID_TEXT, kind, null, "ms06");
    }

    private void emptyResult() {
        when(searchRepository.search(any(Query.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0));
    }

    @Test
    void aUserWithoutAuditPermissionsGets403() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.search(query(null, null, null), NONE));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        verify(searchRepository, never()).search(any(), any());
    }

    @Test
    void askingForAKindTheUserCannotReadGets403() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.search(query(EventKind.REAL, null, null), DRILL));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
    }

    @Test
    void aSinglePermissionNarrowsTheQueryToItsKind() {
        emptyResult();

        service.search(query(null, null, null), DRILL);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchRepository).search(captor.capture(), any(Pageable.class));
        assertEquals(EventKind.SIMULACRO, captor.getValue().getQueryObject().get("tipoEvento"));
    }

    @Test
    void bothPermissionsAddNoKindCondition() {
        emptyResult();

        service.search(query(null, null, null), BOTH);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchRepository).search(captor.capture(), any(Pageable.class));
        assertTrue(captor.getValue().getQueryObject().isEmpty());
    }

    @Test
    void usesDefaultPagingAndSortsNewestFirst() {
        emptyResult();

        service.search(query(null, null, null), BOTH);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchRepository).search(any(Query.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals("fechaHora: DESC,id: DESC", captor.getValue().getSort().toString());
    }

    @Test
    void returnsThePageInTheStandardFormat() {
        Page<AuditEvent> page = new PageImpl<>(List.of(event(EventKind.SIMULACRO)),
                org.springframework.data.domain.PageRequest.of(1, 5), 11);
        when(searchRepository.search(any(Query.class), any(Pageable.class))).thenReturn(page);

        PageResponse<AuditEventResponse> response = service.search(query(null, 1, 5), BOTH);

        assertEquals(1, response.contenido().size());
        assertEquals(1, response.page());
        assertEquals(5, response.size());
        assertEquals(11, response.totalElementos());
        assertEquals(3, response.totalPaginas());
    }

    @Test
    void aSingleUserIdIsAnExactMatchInLowerCase() {
        emptyResult();

        service.search(new AuditQuery(null, null, "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", null, null, null, null, null, null, null, null), BOTH);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchRepository).search(captor.capture(), any(Pageable.class));
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", captor.getValue().getQueryObject().get("usuarioId"));
    }

    @Test
    void severalUserAndEventIdsMatchAnyOfThem() {
        emptyResult();
        String a = "11111111-1111-4111-8111-111111111111";
        String b = "22222222-2222-4222-8222-222222222222";

        service.search(new AuditQuery(null, null, a + "," + b, b + ", " + a, null, null, null, null, null, null, null), BOTH);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(searchRepository).search(captor.capture(), any(Pageable.class));
        org.bson.Document usuarios = (org.bson.Document) captor.getValue().getQueryObject().get("usuarioId");
        org.bson.Document eventos = (org.bson.Document) captor.getValue().getQueryObject().get("eventoId");
        assertEquals(List.of(a, b), usuarios.get("$in"));
        assertEquals(List.of(b, a), eventos.get("$in"));
    }

    @Test
    void rejectsAListWithAnInvalidIdOrTooManyIds() {
        String valido = "11111111-1111-4111-8111-111111111111";
        assertInvalid("usuario_id", new AuditQuery(null, null, valido + ",abc", null, null, null, null, null, null, null, null));
        String muchos = String.join(",", java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> String.format("00000000-0000-4000-8000-%012d", i)).toList());
        assertInvalid("evento_id", new AuditQuery(null, null, null, muchos, null, null, null, null, null, null, null));
    }

    @Test
    void rejectsInvalidFilters() {
        assertInvalid("modulo", new AuditQuery("XX", null, null, null, null, null, null, null, null, null, null));
        assertInvalid("accion", new AuditQuery(null, "login", null, null, null, null, null, null, null, null, null));
        assertInvalid("usuario_id", new AuditQuery(null, null, "abc", null, null, null, null, null, null, null, null));
        assertInvalid("evento_id", new AuditQuery(null, null, null, "abc", null, null, null, null, null, null, null));
        assertInvalid("recurso_afectado",
                new AuditQuery(null, null, null, null, null, "x".repeat(201), null, null, null, null, null));
        assertInvalid("correlation_id",
                new AuditQuery(null, null, null, null, null, null, "x".repeat(201), null, null, null, null));
        assertInvalid("desde", new AuditQuery(null, null, null, null, null, null, null,
                Instant.parse("2026-11-19T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"), null, null));
        assertInvalid("page", query(null, -1, null));
        assertInvalid("size", query(null, null, 0));
        assertInvalid("size", query(null, null, 101));
    }

    private void assertInvalid(String field, AuditQuery query) {
        InvalidFieldException error = assertThrows(InvalidFieldException.class, () -> service.search(query, BOTH));
        assertEquals(field, error.field());
    }

    @Test
    void getReturnsARecordTheUserMayRead() {
        when(repository.findById("id-1")).thenReturn(Optional.of(event(EventKind.SIMULACRO)));

        assertEquals("id-1", service.get("ID-1", DRILL).id());
    }

    @Test
    void getAnswers404WhenItDoesNotExist() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.get("x", BOTH));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.code());
    }

    @Test
    void getAnswers403ForAKindTheUserCannotRead() {
        when(repository.findById("id-1")).thenReturn(Optional.of(event(EventKind.REAL)));
        when(repository.findById("id-2")).thenReturn(Optional.of(event(null)));

        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> service.get("id-1", DRILL)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> service.get("id-2", REAL)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> service.get("id-1", NONE)).code());
    }

    @Test
    void administrativeRecordsNeedBothPermissions() {
        when(repository.findById("id-2")).thenReturn(Optional.of(event(null)));

        assertEquals("id-1", service.get("id-2", BOTH).id());
    }
}
