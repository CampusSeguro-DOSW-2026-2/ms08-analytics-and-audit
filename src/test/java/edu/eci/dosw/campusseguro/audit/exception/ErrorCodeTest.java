package edu.eci.dosw.campusseguro.audit.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Checks the catalog of error codes. */
class ErrorCodeTest {

    @Test
    void mapsEveryKnownStatusToItsCode() {
        assertEquals(ErrorCode.VALIDATION_ERROR, ErrorCode.fromStatus(400));
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.fromStatus(401));
        assertEquals(ErrorCode.FORBIDDEN, ErrorCode.fromStatus(403));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.fromStatus(404));
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.fromStatus(405));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.fromStatus(415));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.fromStatus(503));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromStatus(500));
        assertEquals(ErrorCode.VALIDATION_ERROR, ErrorCode.fromStatus(422));
    }

    @Test
    void usesTheCodesOfTheStandard() {
        assertEquals("VALIDACION", ErrorCode.VALIDATION_ERROR.codigo());
        assertEquals("NO_AUTENTICADO", ErrorCode.UNAUTHORIZED.codigo());
        assertEquals("PROHIBIDO", ErrorCode.FORBIDDEN.codigo());
        assertEquals("SERVICIO_NO_DISPONIBLE", ErrorCode.SERVICE_UNAVAILABLE.codigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.status());
    }

    @Test
    void theResponseNeverHasNullErrors() {
        ErrorResponse response = new ErrorResponse(400, "VALIDACION", "x", null, "id");

        assertEquals(0, response.errores().size());
    }
}
