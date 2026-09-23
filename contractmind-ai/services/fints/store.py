from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from services.fints.crypto import CredentialCipher


@dataclass
class PendingSession:
    session_id: str
    bank_name: str
    blz: str
    bic: str
    fints_url: str
    tan_methods: list[dict[str, str | None]]
    credentials_ciphertext: str
    client_state_ciphertext: str | None = None
    dialog_state_ciphertext: str | None = None
    pending_tan_ciphertext: str | None = None
    decoupled: bool = False
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


@dataclass
class StoredConnection:
    connection_id: str
    bank_name: str
    blz: str
    status: str
    credentials_ciphertext: str
    client_state_ciphertext: str | None = None
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class ConnectionStore:
    """Hält Pending-Sessions nur im RAM. Bestätigte Verbindungen speichern
    ausschließlich Fernet-Ciphertexte (nie PIN/Login im Klartext).
    """

    def __init__(self, cipher: CredentialCipher, data_dir: str | None = None) -> None:
        self._cipher = cipher
        self._sessions: dict[str, PendingSession] = {}
        self._connections: dict[str, StoredConnection] = {}
        self._path = Path(data_dir or os.environ.get("FINTS_DATA_DIR", "data")) / "fints-connections.json"
        self._load()

    def put_session(self, session: PendingSession) -> None:
        self._sessions[session.session_id] = session

    def get_session(self, session_id: str) -> PendingSession | None:
        return self._sessions.get(session_id)

    def pop_session(self, session_id: str) -> PendingSession | None:
        return self._sessions.pop(session_id, None)

    def put_connection(self, connection: StoredConnection) -> None:
        self._connections[connection.connection_id] = connection
        self._persist()

    def get_connection(self, connection_id: str) -> StoredConnection | None:
        return self._connections.get(connection_id)

    def credentials_for(self, connection_id: str) -> dict[str, Any]:
        connection = self.get_connection(connection_id)
        if connection is None:
            return {}
        return self._cipher.decrypt_json(connection.credentials_ciphertext)

    def _load(self) -> None:
        if not self._path.exists():
            return
        try:
            payload = json.loads(self._path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return
        for item in payload.get("connections", []):
            self._connections[item["connection_id"]] = StoredConnection(**item)

    def _persist(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        snapshot = {
            "connections": [
                {
                    "connection_id": item.connection_id,
                    "bank_name": item.bank_name,
                    "blz": item.blz,
                    "status": item.status,
                    "credentials_ciphertext": item.credentials_ciphertext,
                    "client_state_ciphertext": item.client_state_ciphertext,
                    "created_at": item.created_at,
                }
                for item in self._connections.values()
            ]
        }
        self._path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
