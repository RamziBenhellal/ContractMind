from __future__ import annotations

import os
from typing import Any, Protocol

# Öffentlich dokumentierte Product-ID aus dem python-fints-Umfeld (Sparkasse-tauglich).
# Für Produktion sollte ContractMind eine eigene ID bei der Deutschen Kreditwirtschaft
# registrieren: https://www.hbci-zka.de/register/prod_register.htm
DEFAULT_PRODUCT_ID = "6151256F3D4F9975B877BD4A2"


class FinTsBankClient(Protocol):
    init_tan_response: Any

    def get_tan_mechanisms(self) -> dict: ...
    def set_tan_mechanism(self, security_function: Any) -> None: ...
    def send_tan(self, challenge: Any, tan: str) -> Any: ...
    def get_sepa_accounts(self) -> list: ...
    def get_balance(self, account: Any) -> Any: ...
    def get_transactions(self, account: Any, start_date=None, end_date=None) -> Any: ...
    def deconstruct(self, including_private: bool = False) -> bytes: ...
    def pause_dialog(self) -> Any: ...
    def resume_dialog(self, dialog_data: Any) -> Any: ...
    def __enter__(self) -> Any: ...
    def __exit__(self, exc_type, exc, tb) -> Any: ...


class DefaultFinTsClientFactory:
    def create(
        self,
        blz: str,
        login_id: str,
        pin: str,
        server: str,
        from_data: bytes | None = None,
    ) -> FinTsBankClient:
        from fints.client import FinTS3PinTanClient

        kwargs: dict[str, Any] = {
            "product_id": os.environ.get("FINTS_PRODUCT_ID") or DEFAULT_PRODUCT_ID,
        }
        if from_data:
            kwargs["from_data"] = from_data
        client = FinTS3PinTanClient(blz, login_id, pin, server, **kwargs)
        session = getattr(getattr(client, "connection", None), "session", None)
        if session is not None and callable(getattr(session, "request", None)):
            original = session.request

            def request_with_timeout(method, url, **request_kwargs):
                request_kwargs.setdefault("timeout", 30)
                return original(method, url, **request_kwargs)

            session.request = request_with_timeout
        return client
