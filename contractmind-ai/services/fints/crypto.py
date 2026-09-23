import base64
import hashlib
import json
import os
from typing import Any

from cryptography.fernet import Fernet, InvalidToken


class CredentialCipher:
    """Fernet (AES-128-CBC + HMAC) over a SHA-256 derived key.

    Der Key kommt ausschließlich aus ``FINTS_ENCRYPTION_KEY``. PIN und
    Login-Kennung werden niemals im Klartext persistiert.
    """

    def __init__(self, secret: str | None = None) -> None:
        raw = secret if secret is not None else os.environ.get(
            "FINTS_ENCRYPTION_KEY", "dev-only-not-for-production"
        )
        if not raw.strip():
            raise RuntimeError(
                "FINTS_ENCRYPTION_KEY fehlt. Ohne Key dürfen FinTS-Zugangsdaten nicht gespeichert werden."
            )
        digest = hashlib.sha256(raw.encode("utf-8")).digest()
        self._fernet = Fernet(base64.urlsafe_b64encode(digest))

    def encrypt_json(self, payload: dict[str, Any]) -> str:
        blob = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        return self._fernet.encrypt(blob).decode("ascii")

    def decrypt_json(self, token: str) -> dict[str, Any]:
        try:
            raw = self._fernet.decrypt(token.encode("ascii"))
        except InvalidToken as exc:
            raise ValueError("Zugangsdaten konnten nicht entschlüsselt werden.") from exc
        data = json.loads(raw.decode("utf-8"))
        if not isinstance(data, dict):
            raise ValueError("Ungültiges Zugangsdaten-Format.")
        return data

    def encrypt_bytes(self, data: bytes) -> str:
        return self._fernet.encrypt(data).decode("ascii")

    def decrypt_bytes(self, token: str) -> bytes:
        try:
            return self._fernet.decrypt(token.encode("ascii"))
        except InvalidToken as exc:
            raise ValueError("Sitzungsdaten konnten nicht entschlüsselt werden.") from exc
