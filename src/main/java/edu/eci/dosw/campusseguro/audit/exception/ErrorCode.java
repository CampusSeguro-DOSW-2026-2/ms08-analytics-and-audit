package edu.eci.dosw.campusseguro.audit.exception;

import org.springframework.http.HttpStatus;

/** Catalog of API error codes (API standards, section 7.2). Each code sets its HTTP status. */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDACION",
            "Los datos enviados no son válidos"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
            "Se requiere autenticación o la identidad no es válida"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "PROHIBIDO",
            "No tienes permiso para realizar esta operación"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "NO_ENCONTRADO",
            "El recurso solicitado no existe"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
            "El método HTTP no está permitido"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_NO_SOPORTADO",
            "El tipo de contenido no está soportado"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
            "El servicio no está disponible. Intenta de nuevo más tarde"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
            "Ocurrió un error inesperado");

    private final HttpStatus status;
    private final String codigo;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String codigo, String defaultMessage) {
        this.status = status;
        this.codigo = codigo;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    /** Value written in the {@code codigo} field of the response. */
    public String codigo() {
        return codigo;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Maps an HTTP status to its code (errors raised by Spring or the web server). */
    public static ErrorCode fromStatus(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> VALIDATION_ERROR;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> httpStatus >= 500 ? INTERNAL_ERROR : VALIDATION_ERROR;
        };
    }
}
