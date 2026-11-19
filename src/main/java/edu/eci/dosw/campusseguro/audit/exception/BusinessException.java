package edu.eci.dosw.campusseguro.audit.exception;

/** A rule of the service was broken. It carries the error code that the API must answer. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
