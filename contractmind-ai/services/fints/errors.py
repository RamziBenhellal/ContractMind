class FinTsError(Exception):
    def __init__(self, code: str, message: str, status_code: int) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


def invalid_pin() -> FinTsError:
    return FinTsError("INVALID_PIN", "Login-ID oder PIN stimmen nicht.", 401)


def tan_invalid(message: str | None = None) -> FinTsError:
    return FinTsError(
        "TAN_INVALID",
        message or "Die eingegebene TAN ist nicht korrekt. Bitte versuche es erneut.",
        400,
    )


def tan_expired() -> FinTsError:
    return FinTsError("TAN_EXPIRED", "Die TAN ist abgelaufen. Bitte starte die Verbindung neu.", 410)


def bank_unreachable(message: str | None = None) -> FinTsError:
    return FinTsError(
        "BANK_UNREACHABLE",
        message or "Die Bank ist über FinTS gerade nicht erreichbar.",
        503,
    )


def tan_required(message: str | None = None) -> FinTsError:
    return FinTsError(
        "TAN_REQUIRED",
        message or "Die Bank verlangt eine neue TAN. Bitte die Verbindung erneut bestätigen.",
        409,
    )


def decryption_error(message: str | None = None) -> FinTsError:
    return FinTsError(
        "DECRYPTION_ERROR",
        message or "Gespeicherte Zugangsdaten konnten nicht entschlüsselt werden.",
        500,
    )


def search_failed() -> FinTsError:
    return FinTsError("SEARCH_FAILED", "Die Banksuche ist fehlgeschlagen.", 502)


def not_found() -> FinTsError:
    return FinTsError("UNKNOWN", "Bankverbindung nicht gefunden.", 404)
