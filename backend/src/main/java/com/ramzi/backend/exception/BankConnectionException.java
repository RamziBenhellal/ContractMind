package com.ramzi.backend.exception;

import org.springframework.http.HttpStatus;

public class BankConnectionException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Integer retryAfterSeconds;

    public BankConnectionException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public BankConnectionException(String code, String message, HttpStatus status, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
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

    public static BankConnectionException tanRequired() {
        return new BankConnectionException(
                "TAN_REQUIRED",
                "Die Bank verlangt eine neue TAN. Bitte die Verbindung erneut bestätigen.",
                HttpStatus.CONFLICT);
    }

    public static BankConnectionException connectionExpired() {
        return new BankConnectionException(
                "CONNECTION_EXPIRED",
                "Die Bankverbindung ist abgelaufen. Bitte verbinde dein Konto erneut.",
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

    public static BankConnectionException bankUnavailable() {
        return bankUnavailable(null);
    }

    public static BankConnectionException bankUnavailable(String detail) {
        String message = (detail != null && !detail.isBlank())
                ? detail
                : "Deine Bank ist gerade nicht erreichbar. Bitte versuche es später noch einmal.";
        return new BankConnectionException("BANK_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static BankConnectionException timeout() {
        return new BankConnectionException(
                "TIMEOUT",
                "Die Bank hat nicht rechtzeitig geantwortet. Bitte versuche es später erneut.",
                HttpStatus.GATEWAY_TIMEOUT);
    }

    public static BankConnectionException decryptionError() {
        return new BankConnectionException(
                "DECRYPTION_ERROR",
                "Gespeicherte Zugangsdaten konnten nicht entschlüsselt werden. Bitte verbinde dein Konto erneut.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static BankConnectionException rateLimited(int retryAfterSeconds) {
        int wait = Math.max(1, retryAfterSeconds);
        return new BankConnectionException(
                "TOO_MANY_REQUESTS",
                "Der letzte Sync liegt weniger als das Mindestintervall zurück. Nächster Sync in "
                        + wait + " Sekunden.",
                HttpStatus.TOO_MANY_REQUESTS,
                wait);
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
