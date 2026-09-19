package com.ramzi.backend.exception;

import org.springframework.http.HttpStatus;

public class BankConnectionException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BankConnectionException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BankConnectionException invalidPin() {
        return new BankConnectionException(
                "INVALID_PIN",
                "Login-ID oder PIN stimmen nicht. Bitte prüfe deine Eingaben.",
                HttpStatus.UNAUTHORIZED);
    }

    public static BankConnectionException tanInvalid() {
        return new BankConnectionException(
                "TAN_INVALID",
                "Die eingegebene TAN ist nicht korrekt. Bitte versuche es erneut.",
                HttpStatus.BAD_REQUEST);
    }

    public static BankConnectionException tanExpired() {
        return new BankConnectionException(
                "TAN_EXPIRED",
                "Die TAN ist abgelaufen. Bitte melde dich erneut an.",
                HttpStatus.GONE);
    }

    public static BankConnectionException bankUnreachable() {
        return bankUnreachable(null);
    }

    public static BankConnectionException bankUnreachable(String detail) {
        String message = (detail != null && !detail.isBlank())
                ? detail
                : "Deine Bank ist gerade nicht erreichbar. Bitte versuche es später noch einmal.";
        return new BankConnectionException("BANK_UNREACHABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static BankConnectionException searchFailed() {
        return new BankConnectionException(
                "SEARCH_FAILED",
                "Die Banksuche ist fehlgeschlagen. Bitte versuche es erneut.",
                HttpStatus.BAD_GATEWAY);
    }

    public static BankConnectionException notFound() {
        return new BankConnectionException(
                "UNKNOWN",
                "Bankverbindung nicht gefunden.",
                HttpStatus.NOT_FOUND);
    }

    public static BankConnectionException notPending() {
        return new BankConnectionException(
                "UNKNOWN",
                "Diese Verbindung wartet nicht auf eine TAN-Bestätigung.",
                HttpStatus.CONFLICT);
    }

    public static BankConnectionException unknown(String message) {
        return new BankConnectionException(
                "UNKNOWN",
                message != null ? message : "Es ist ein unerwarteter Fehler aufgetreten.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
