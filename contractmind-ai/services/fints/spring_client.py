from __future__ import annotations

import os
from typing import Any

import httpx

from models.fints import SyncedAccount


class SpringClassificationClient:
    """Schickt normalisierte Umsätze an Spring, das persistiert und klassifiziert."""

    def __init__(self, base_url: str | None = None, token: str | None = None) -> None:
        self.base_url = (base_url or os.environ.get("SPRING_BOOT_URL", "http://localhost:8080")).rstrip("/")
        self.token = token or os.environ.get("FINTS_INTERNAL_TOKEN", "dev-internal")

    def forward_transactions(self, python_session_id: str, accounts: list[SyncedAccount]) -> bool:
        payload: dict[str, Any] = {
            "pythonSessionId": python_session_id,
            "accounts": [account.model_dump(by_alias=True, mode="json") for account in accounts],
        }
        try:
            response = httpx.post(
                f"{self.base_url}/api/internal/fints/transactions",
                json=payload,
                headers={"X-Internal-Token": self.token, "Content-Type": "application/json"},
                timeout=15.0,
            )
            return 200 <= response.status_code < 300
        except httpx.HTTPError:
            return False
