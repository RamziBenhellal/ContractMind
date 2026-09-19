from __future__ import annotations

import logging
import uuid
from datetime import date, timedelta
from decimal import Decimal
from typing import Any, Callable

logger = logging.getLogger(__name__)

from models.fints import (
    BankInfo,
    ConfirmTanResponse,
    FinTsAccount,
    SelectTanMethodResponse,
    StartConnectionRequest,
    StartConnectionResponse,
    SyncedAccount,
    TanMethod,
    TransactionFetchResponse,
)
from services.fints.bank_directory import BankDirectory
from services.fints.client_factory import DefaultFinTsClientFactory, FinTsBankClient
from services.fints.crypto import CredentialCipher
from services.fints.errors import (
    FinTsError,
    bank_unreachable,
    invalid_pin,
    not_found,
    tan_expired,
    tan_invalid,
)
from services.fints.normalize import normalize_transaction
from services.fints.spring_client import SpringClassificationClient
from services.fints.store import ConnectionStore, PendingSession, StoredConnection

# PSD2 / TAN-Verfahren:
# Seit PSD2 (SCA) verlangen die meisten deutschen Banken für den ersten Dialog
# und oft auch für Kontoauszüge eine starke Kundenauthentifizierung. chipTAN,
# pushTAN, photoTAN und App-Freigaben sind KEINE klare API: Der Nutzer muss den
# Challenge-Code am TAN-Gerät bzw. in der Bank-App bestätigen. Dieser Dienst
# kann den Dialog nur aufbauen, die verfügbaren Verfahren listen und eine
# eingegebene TAN weiterreichen. Ein reiner Server-zu-Server-Call ohne
# Nutzerinteraktion ist bei diesen Verfahren nicht möglich. Nach der ersten
# Freigabe speichern wir nur den verschlüsselten Client-State (System-ID),
# damit Folgesyncs seltener eine neue TAN brauchen – die Bank entscheidet das.


class FinTsConnector:
    def __init__(
        self,
        cipher: CredentialCipher | None = None,
        store: ConnectionStore | None = None,
        directory: BankDirectory | None = None,
        client_factory: DefaultFinTsClientFactory | None = None,
        spring_client: SpringClassificationClient | None = None,
        lookback_days: int = 90,
    ) -> None:
        self._cipher = cipher or CredentialCipher()
        self._store = store or ConnectionStore(self._cipher)
        self._directory = directory or BankDirectory()
        self._factory = client_factory or DefaultFinTsClientFactory()
        self._spring = spring_client or SpringClassificationClient()
        self._lookback_days = lookback_days

    def search_banks(self, query: str) -> list[BankInfo]:
        try:
            return [
                BankInfo(blz=bank.blz, name=bank.name, bic=bank.bic, fints_url=bank.fints_url)
                for bank in self._directory.search(query)
            ]
        except Exception as exc:
            raise bank_unreachable(str(exc)) from exc

    def start_connection(self, request: StartConnectionRequest) -> StartConnectionResponse:
        if not request.blz and not request.bic:
            raise bank_unreachable("BLZ oder BIC ist erforderlich.")
        bank = self._directory.resolve(request.blz, request.bic, request.fints_url)
        if bank is None or not bank.fints_url:
            raise bank_unreachable("Für diese BLZ/BIC ist keine FinTS-PIN/TAN-URL hinterlegt.")

        session_id = str(uuid.uuid4())
        credentials = {
            "blz": bank.blz,
            "bic": bank.bic,
            "loginId": request.login_id,
            "pin": request.pin,
            "fintsUrl": bank.fints_url,
        }
        ciphertext = self._cipher.encrypt_json(credentials)

        client = self._create_client(bank.blz, request.login_id, request.pin, bank.fints_url)
        tan_methods: list[TanMethod] = []
        pending_tan: str | None = None
        client_state: str | None = None
        dialog_state: str | None = None

        try:
            # Kein Standing-Dialog (`with client`) vor der TAN-Auswahl – Sparkassen
            # brechen den Dialog sonst oft mit 9050/9800 ab. Fehler hier nicht
            # schlucken: sonst wirkt die Bank „unerreichbar“, obwohl sie antwortet.
            fetch = getattr(client, "fetch_tan_mechanisms", None)
            if callable(fetch):
                fetch()
            tan_methods = self._read_tan_methods(client)
            if not tan_methods:
                raise bank_unreachable(
                    "Die Bank hat keine TAN-Verfahren geliefert. Nutze den Anmeldename aus dem Online-Banking (nicht die Kontonummer) und die PIN."
                )
            pending = getattr(client, "init_tan_response", None)
            if _is_need_tan(pending):
                pending_tan = self._cipher.encrypt_bytes(pending.get_data())
            client_raw = _safe_call(client, "deconstruct", including_private=True)
            if client_raw:
                client_state = self._cipher.encrypt_bytes(_as_bytes(client_raw))
        except FinTsError:
            raise
        except Exception as exc:
            logger.exception("FinTS-Start fehlgeschlagen für BLZ %s (%s)", bank.blz, type(exc).__name__)
            raise _map_client_error(exc) from exc

        self._store.put_session(
            PendingSession(
                session_id=session_id,
                bank_name=bank.name,
                blz=bank.blz,
                bic=bank.bic,
                fints_url=bank.fints_url,
                tan_methods=[method.model_dump() for method in tan_methods],
                credentials_ciphertext=ciphertext,
                client_state_ciphertext=client_state,
                dialog_state_ciphertext=dialog_state,
                pending_tan_ciphertext=pending_tan,
            )
        )
        return StartConnectionResponse(
            connection_id=session_id,
            session_id=session_id,
            bank_name=bank.name,
            tan_methods=tan_methods,
        )

    def select_tan_method(self, session_id: str, tan_method_id: str) -> SelectTanMethodResponse:
        session = self._store.get_session(session_id)
        if session is None:
            raise tan_expired() if session_id else not_found()
        if not tan_method_id:
            raise tan_invalid()

        credentials = self._cipher.decrypt_json(session.credentials_ciphertext)
        client = self._restore_client(credentials, session.client_state_ciphertext)
        pending: Any = None
        dialog_state: str | None = None

        try:
            # Verfahren und Medium müssen vor dem Standing-Dialog gesetzt werden,
            # sonst schickt die Bank keine pushTAN-Freigabe.
            client.set_tan_mechanism(tan_method_id)
            self._select_tan_medium(client)
            with client:
                pending = getattr(client, "init_tan_response", None)
                if not _is_need_tan(pending):
                    result = client.get_sepa_accounts()
                    if _is_need_tan(result):
                        pending = result
                if _is_need_tan(pending):
                    if _is_push_method(tan_method_id, session.tan_methods):
                        pending.decoupled = True
                    paused = client.pause_dialog()
                    if paused:
                        dialog_state = self._cipher.encrypt_bytes(_as_bytes(paused))
        except FinTsError:
            raise
        except Exception as exc:
            logger.exception("FinTS-TAN-Auswahl fehlgeschlagen für Session %s", session_id)
            raise _map_client_error(exc) from exc

        pending_tan = None
        decoupled = False
        challenge = None
        if _is_need_tan(pending):
            pending_tan = self._cipher.encrypt_bytes(pending.get_data())
            decoupled = bool(getattr(pending, "decoupled", False)) or _is_push_method(
                tan_method_id, session.tan_methods
            )
            raw_challenge = getattr(pending, "challenge", None)
            if raw_challenge:
                challenge = str(raw_challenge)

        client_state = None
        raw_state = _safe_call(client, "deconstruct", including_private=True)
        if raw_state:
            client_state = self._cipher.encrypt_bytes(_as_bytes(raw_state))

        self._store.put_session(
            PendingSession(
                session_id=session.session_id,
                bank_name=session.bank_name,
                blz=session.blz,
                bic=session.bic,
                fints_url=session.fints_url,
                tan_methods=session.tan_methods,
                credentials_ciphertext=session.credentials_ciphertext,
                client_state_ciphertext=client_state,
                dialog_state_ciphertext=dialog_state,
                pending_tan_ciphertext=pending_tan,
                decoupled=decoupled,
            )
        )
        return SelectTanMethodResponse(
            hint=_challenge_hint(challenge, decoupled, tan_method_id, session.tan_methods),
            decoupled=decoupled,
            challenge=challenge,
        )

    def confirm_tan(self, session_id: str, tan_method_id: str | None, tan: str) -> ConfirmTanResponse:
        session = self._store.get_session(session_id)
        if session is None:
            raise tan_expired() if session_id else not_found()

        credentials = self._cipher.decrypt_json(session.credentials_ciphertext)
        client = self._restore_client(credentials, session.client_state_ciphertext)
        # Verfahren nicht mehr ändern: python-fints speichert decoupled nicht in get_data(),
        # und ein neues Verfahren vor dem Resume erzeugt 9340 (ungültige Signatur).
        if tan_method_id and not session.dialog_state_ciphertext:
            _safe_call(client, "set_tan_mechanism", tan_method_id)

        try:
            with self._resume(client, session.dialog_state_ciphertext):
                pending = self._pending_tan(client, session)
                result = None
                if pending is not None:
                    if session.decoupled or _is_push_method(tan_method_id, session.tan_methods):
                        pending.decoupled = True
                    result = client.send_tan(pending, tan or "")
                    if _is_need_tan(result):
                        raise tan_invalid(
                            "Die Freigabe ist in der App noch nicht bestätigt. "
                            "Bitte in der S-pushTAN-App bestätigen und danach hier erneut auf Bestätigen tippen."
                        )
                accounts = self._accounts_from_result(result, session.bank_name)
                if accounts is None:
                    accounts = self._load_accounts(client, session.bank_name, include_transactions=False)
        except FinTsError:
            raise
        except Exception as exc:
            raise _map_client_error(exc, tan_context=True) from exc

        client_state = None
        raw_state = _safe_call(client, "deconstruct", including_private=True)
        if raw_state:
            client_state = self._cipher.encrypt_bytes(_as_bytes(raw_state))

        persisted_credentials = dict(credentials)
        if tan_method_id:
            persisted_credentials["tanMethodId"] = tan_method_id
        self._store.put_connection(
            StoredConnection(
                connection_id=session.session_id,
                bank_name=session.bank_name,
                blz=session.blz,
                status="ACTIVE",
                credentials_ciphertext=self._cipher.encrypt_json(persisted_credentials),
                client_state_ciphertext=client_state,
            )
        )
        self._store.pop_session(session.session_id)
        return ConfirmTanResponse(
            connection_id=session.session_id,
            accounts=[
                FinTsAccount(
                    iban=account.iban,
                    account_type=account.account_type,
                    balance=account.balance,
                    bank_name=account.bank_name,
                )
                for account in accounts
            ],
        )

    def fetch_transactions(self, connection_id: str) -> TransactionFetchResponse:
        connection = self._store.get_connection(connection_id)
        if connection is None:
            raise not_found()
        credentials = self._cipher.decrypt_json(connection.credentials_ciphertext)
        accounts = self._sync_with_credentials(credentials, connection.bank_name, connection.client_state_ciphertext)
        forwarded = self._spring.forward_transactions(connection_id, accounts)
        return TransactionFetchResponse(
            connection_id=connection_id,
            accounts=accounts,
            forwarded_to_backend=forwarded,
        )

    def sync(self, blz: str, login_id: str, pin: str, fints_url: str | None = None) -> list[SyncedAccount]:
        bank = self._directory.resolve(blz, None, fints_url)
        if bank is None or not bank.fints_url:
            raise bank_unreachable("Für diese BLZ ist keine FinTS-URL hinterlegt.")
        credentials = {"blz": bank.blz, "loginId": login_id, "pin": pin, "fintsUrl": bank.fints_url}
        return self._sync_with_credentials(credentials, bank.name, None)

    def _sync_with_credentials(
        self,
        credentials: dict[str, Any],
        bank_name: str,
        client_state_ciphertext: str | None,
    ) -> list[SyncedAccount]:
        client = self._restore_client(credentials, client_state_ciphertext)
        try:
            with client:
                pending = getattr(client, "init_tan_response", None)
                if _is_need_tan(pending):
                    # PSD2: chipTAN/pushTAN können hier nicht server-seitig abgeschlossen werden.
                    raise bank_unreachable(
                        "Die Bank verlangt eine TAN (chipTAN/pushTAN). Bitte die Verbindung neu bestätigen."
                    )
                return self._load_accounts(client, bank_name, include_transactions=True)
        except FinTsError:
            raise
        except Exception as exc:
            raise _map_client_error(exc) from exc

    def _load_accounts(self, client: FinTsBankClient, bank_name: str, include_transactions: bool) -> list[SyncedAccount]:
        sepa_accounts = client.get_sepa_accounts() or []
        synced: list[SyncedAccount] = []
        start = date.today() - timedelta(days=self._lookback_days)
        end = date.today()
        for account in sepa_accounts:
            iban = getattr(account, "iban", None)
            account_type = (
                getattr(account, "account_type", None)
                or getattr(account, "product_name", None)
                or "Girokonto"
            )
            balance = _balance_of(_safe_call(client, "get_balance", account))
            transactions = []
            if include_transactions:
                raw = client.get_transactions(account, start_date=start, end_date=end)
                if _is_need_tan(raw):
                    raise bank_unreachable(
                        "Kontoauszug erfordert eine TAN (chipTAN/pushTAN) – kein reiner API-Call."
                    )
                transactions = [normalize_transaction(item) for item in (raw or [])]
            synced.append(
                SyncedAccount(
                    iban=iban,
                    account_type=str(account_type),
                    balance=balance,
                    bank_name=bank_name,
                    transactions=transactions,
                )
            )
        return synced

    def _create_client(self, blz: str, login_id: str, pin: str, server: str, from_data: bytes | None = None):
        return self._factory.create(blz, login_id, pin, server, from_data=from_data)

    def _restore_client(self, credentials: dict[str, Any], client_state_ciphertext: str | None):
        from_data = self._cipher.decrypt_bytes(client_state_ciphertext) if client_state_ciphertext else None
        return self._create_client(
            credentials["blz"],
            credentials["loginId"],
            credentials["pin"],
            credentials["fintsUrl"],
            from_data=from_data,
        )

    def _resume(self, client: FinTsBankClient, dialog_state_ciphertext: str | None):
        if dialog_state_ciphertext:
            dialog = self._cipher.decrypt_bytes(dialog_state_ciphertext)
            resumed = _safe_call(client, "resume_dialog", dialog)
            if resumed is not None:
                return resumed
        return client

    def _pending_tan(self, client: FinTsBankClient, session: PendingSession) -> Any:
        pending = None
        if session.pending_tan_ciphertext:
            raw = self._cipher.decrypt_bytes(session.pending_tan_ciphertext)
            loader = _need_tan_loader()
            if loader is not None:
                try:
                    pending = loader(raw)
                except Exception:
                    logger.warning("NeedTAN-State konnte nicht wiederhergestellt werden, nutze Live-Challenge.")
        if pending is None:
            pending = getattr(client, "init_tan_response", None)
        if pending is not None and session.decoupled:
            pending.decoupled = True
        return pending

    def _select_tan_medium(self, client: FinTsBankClient) -> None:
        required = _safe_call(client, "is_tan_media_required")
        if not required:
            return
        media = _safe_call(client, "get_tan_media")
        media_list = media[1] if isinstance(media, tuple) and len(media) > 1 else media
        if not media_list:
            return
        first = media_list[0]
        setter = getattr(client, "set_tan_medium", None)
        if callable(setter):
            setter(first)

    def _accounts_from_result(self, raw: Any, bank_name: str) -> list[SyncedAccount] | None:
        if not raw or _is_need_tan(raw) or not isinstance(raw, list):
            return None
        accounts: list[SyncedAccount] = []
        for item in raw:
            iban = item.get("iban") if isinstance(item, dict) else getattr(item, "iban", None)
            if not iban and not hasattr(item, "iban"):
                return None
            account_type = (
                (item.get("account_type") if isinstance(item, dict) else None)
                or getattr(item, "account_type", None)
                or getattr(item, "product_name", None)
                or "Girokonto"
            )
            accounts.append(
                SyncedAccount(
                    iban=iban,
                    account_type=str(account_type),
                    balance=_balance_of(item) if not isinstance(item, dict) else None,
                    bank_name=bank_name,
                    transactions=[],
                )
            )
        return accounts

    def _read_tan_methods(self, client: FinTsBankClient) -> list[TanMethod]:
        mechanisms = client.get_tan_mechanisms() or {}
        methods: list[TanMethod] = []
        for key, params in mechanisms.items():
            name = getattr(params, "name", None) or str(key)
            methods.append(TanMethod(id=str(key), name=str(name), hint=_tan_hint(str(name))))
        return methods


def _tan_hint(name: str) -> str:
    lower = name.lower()
    if "chiptan" in lower or "flicker" in lower:
        return "chipTAN: Nach der Auswahl die TAN am Generator erzeugen."
    if "pushtan" in lower or "push" in lower or "decoupled" in lower:
        return "pushTAN: Die Freigabe kommt erst nach der Auswahl in die Banking-App."
    if "phototan" in lower:
        return "photoTAN: Grafik in der App scannen."
    if "sms" in lower:
        return "smsTAN: TAN kommt per SMS."
    return "PSD2-SCA: Viele Banken verlangen chipTAN/pushTAN außerhalb dieses API-Calls."


def _challenge_hint(
    challenge: str | None,
    decoupled: bool,
    tan_method_id: str,
    tan_methods: list[dict[str, str | None]],
) -> str:
    if challenge:
        return challenge
    selected = next((item for item in tan_methods if str(item.get("id")) == str(tan_method_id)), None)
    name = ((selected or {}).get("name") or tan_method_id).lower()
    if decoupled or "push" in name:
        return "Die Freigabe wurde an deine S-pushTAN-App geschickt. Bestätige sie dort und tippe danach auf Bestätigen."
    if "chiptan" in name or "flicker" in name:
        return "Erzeuge jetzt die TAN am Generator und gib sie hier ein."
    return "Gib die TAN ein, die dein gewähltes Verfahren jetzt anzeigt."


def _is_need_tan(result: Any) -> bool:
    if result is None:
        return False
    name = type(result).__name__
    if name in {"NeedTANResponse", "NeedRetryResponse"}:
        return True
    return bool(getattr(result, "challenge", None)) and hasattr(result, "get_data")


def _need_tan_loader() -> Callable[[bytes], Any] | None:
    try:
        from fints.client import NeedRetryResponse

        return NeedRetryResponse.from_data
    except Exception:
        return None


def _safe_call(obj: Any, method: str, *args, **kwargs) -> Any:
    fn = getattr(obj, method, None)
    if not callable(fn):
        return None
    try:
        return fn(*args, **kwargs)
    except TypeError:
        try:
            return fn(*args)
        except Exception:
            return None
    except Exception:
        return None


def _as_bytes(value: Any) -> bytes:
    if isinstance(value, bytes):
        return value
    if isinstance(value, str):
        return value.encode("utf-8")
    return str(value).encode("utf-8")


def _balance_of(raw: Any) -> Decimal | None:
    if raw is None:
        return None
    amount = getattr(raw, "amount", raw)
    inner = getattr(amount, "amount", amount)
    try:
        return Decimal(str(inner))
    except Exception:
        return None


def _exception_chain(exc: BaseException) -> list[BaseException]:
    chain: list[BaseException] = []
    seen: set[int] = set()
    current: BaseException | None = exc
    while current is not None and id(current) not in seen:
        seen.add(id(current))
        chain.append(current)
        current = current.__cause__ or current.__context__
    return chain


def _is_push_method(tan_method_id: str | None, tan_methods: list[dict[str, str | None]] | None) -> bool:
    needle = (tan_method_id or "").lower()
    if "push" in needle or "decoupled" in needle:
        return True
    for item in tan_methods or []:
        if str(item.get("id")) != str(tan_method_id):
            continue
        name = f"{item.get('name') or ''} {item.get('hint') or ''}".lower()
        return "push" in name or "decoupled" in name
    return False


def _map_client_error(exc: Exception, tan_context: bool = False) -> FinTsError:
    chain = _exception_chain(exc)
    names = " ".join(type(item).__name__.lower() for item in chain)
    text = " ".join(str(item) for item in chain).lower()
    if tan_context and (
        "9340" in text
        or "signatur" in text
        or "9800" in text
        or "9050" in text
        or "fintsclientpinerror" in names
        or "pin wrong" in text
    ):
        return tan_invalid(
            "Die Bank hat die Freigabe nicht zugeordnet. Bitte in der S-pushTAN-App bestätigen "
            "und danach hier auf Bestätigen tippen. Wenn du schon bestätigt hast, starte die Verbindung neu."
        )
    if "fintsclientpinerror" in names or "pin wrong" in text or "pin falsch" in text:
        return invalid_pin()
    if "kennung" in text or "legitimations" in text:
        return invalid_pin()
    if "temporaryauth" in names or "temporarily locked" in text:
        return bank_unreachable("Das Online-Banking-Konto ist vorübergehend gesperrt.")
    if "expir" in text:
        return tan_expired()
    if tan_context or ("tan" in text and "mechanismus" not in text and "tan-verfahren" not in text):
        return tan_invalid()
    if "product" in text or "registr" in text:
        return bank_unreachable(
            "Die Bank hat die FinTS-Product-ID abgelehnt. Für den Produktivbetrieb eine eigene ID bei der DK registrieren."
        )
    if (
        "could not fetch bpd" in text
        or "fintsdialoginiterror" in names
        or "dialog initialization" in text
        or "authentication data wrong" in text
    ):
        return bank_unreachable(
            "Die Bank hat den FinTS-Dialog abgelehnt. Nutze den Anmeldename aus dem Online-Banking (nicht die Kontonummer) und die PIN."
        )
    if "fintsconnectionerror" in names:
        return bank_unreachable("Die FinTS-Verbindung zur Bank ist fehlgeschlagen.")
    detail = str(exc).strip() or None
    if detail and len(detail) > 280:
        detail = detail[:280] + "…"
    return bank_unreachable(detail)
