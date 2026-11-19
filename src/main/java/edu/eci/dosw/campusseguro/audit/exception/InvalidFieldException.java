package edu.eci.dosw.campusseguro.audit.exception;

/** A field breaks a business rule of the contract: answers 400 with the field as detail. */
public class InvalidFieldException extends BusinessException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
