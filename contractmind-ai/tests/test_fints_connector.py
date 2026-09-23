from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path

from models.fints import StartConnectionRequest
from services.fints.bank_directory import BankDirectory
from services.fints.connector import FinTsConnector
from services.fints.crypto import CredentialCipher
from services.fints.normalize import normalize_transaction
from services.fints.store import ConnectionStore


class FakeTan:
    def __init__(self, name: str) -> None:
        self.name = name


class FakeAccount:
    def __init__(self, iban: str) -> None:
        self.iban = iban
        self.account_type = "Girokonto"


class FakeBalance:
    def __init__(self, amount: Decimal) -> None:
        self.amount = type("Amount", (), {"amount": amount})()


class FakeTx:
    def __init__(self, **data) -> None:
        self.data = data


class FakeClient:
    def __init__(self, pin: str = "secret-pin") -> None:
        self.pin = pin
        self.init_tan_response = None
        self.sent_tan = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def fetch_tan_mechanisms(self):
        return None

    def get_tan_mechanisms(self):
        if self.pin == "bad":
            raise RuntimeError("PIN falsch")
        return {"921": FakeTan("chipTAN comfort")}

    def set_tan_mechanism(self, method_id):
        self.method_id = method_id

    def send_tan(self, challenge, tan):
        if tan != "123456":
            raise RuntimeError("TAN ungueltig")
        self.sent_tan = tan
        return "ok"

    def get_sepa_accounts(self):
        return [FakeAccount("DE89370400440532013000")]

    def get_balance(self, account):
        return FakeBalance(Decimal("1420.50"))

    def get_transactions(self, account, start_date=None, end_date=None):
        return [
            FakeTx(
                date=date(2026, 9, 18),
                amount=Decimal("-12.50"),
                purpose="Netflix",
                applicant_name="Netflix International",
                applicant_iban="DE02120300000000202051",
            )
        ]

    def deconstruct(self, including_private=False):
        return b"client-state"

    def pause_dialog(self):
        return b"dialog-state"


class FakeFactory:
    def __init__(self, client: FakeClient) -> None:
        self.client = client

    def create(self, blz, login_id, pin, server, from_data=None):
        self.client.pin = pin
        self.last = (blz, login_id, pin, server, from_data)
        return self.client


class FakeSpring:
    def __init__(self) -> None:
        self.calls = []

    def forward_transactions(self, python_session_id, accounts):
        self.calls.append((python_session_id, accounts))
        return True


def _connector(tmp_path: Path, pin: str = "secret-pin") -> tuple[FinTsConnector, FakeSpring, ConnectionStore]:
    cipher = CredentialCipher("unit-test-fints-key")
    store = ConnectionStore(cipher, data_dir=str(tmp_path))
    spring = FakeSpring()
    connector = FinTsConnector(
        cipher=cipher,
        store=store,
        client_factory=FakeFactory(FakeClient(pin)),
        spring_client=spring,
    )
    return connector, spring, store


def test_search_banks_by_blz():
    connector, _, _ = _connector(Path("."))
    result = connector.search_banks("50050201")
    assert result[0].name == "Frankfurter Sparkasse"
    assert result[0].bic == "HELADEF1822"


def test_search_sparkasse_hildesheim_goslar_peine():
    directory = BankDirectory()
    hits = directory.search("hildesheim goslar peine")
    assert any(bank.blz == "25950130" for bank in hits)
    typo = directory.search("sparkasse hildeheim")
    assert any(bank.blz == "25950130" for bank in typo)
    by_blz = directory.search("25950130")
    assert by_blz[0].name == "Sparkasse Hildesheim Goslar Peine"


def test_start_connection_returns_tan_methods_and_never_stores_pin_plaintext(tmp_path):
    connector, _, store = _connector(tmp_path)
    response = connector.start_connection(
        StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
    )

    assert response.session_id == response.connection_id
    assert response.tan_methods[0].id == "921"
    assert "chipTAN" in response.tan_methods[0].name
    assert "Generator" in (response.tan_methods[0].hint or "")

    session = store.get_session(response.session_id)
    assert session is not None
    assert "secret-pin" not in session.credentials_ciphertext
    creds = connector._cipher.decrypt_json(session.credentials_ciphertext)
    assert creds["pin"] == "secret-pin"
    assert creds["loginId"] == "user1"


def test_confirm_tan_persists_only_ciphertext(tmp_path):
    connector, _, store = _connector(tmp_path)
    started = connector.start_connection(
        StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
    )
    confirmed = connector.confirm_tan(started.session_id, "921", "123456")

    assert confirmed.accounts[0].iban == "DE89370400440532013000"
    assert confirmed.accounts[0].balance == Decimal("1420.50")
    assert confirmed.accounts[0].transactions[0].purpose == "Netflix"
    stored = store.get_connection(started.session_id)
    assert stored is not None
    assert stored.status == "ACTIVE"
    assert "secret-pin" not in stored.credentials_ciphertext
    on_disk = (tmp_path / "fints-connections.json").read_text(encoding="utf-8")
    assert "secret-pin" not in on_disk
    assert store.get_session(started.session_id) is None


def test_fetch_transactions_normalizes_and_forwards_to_spring(tmp_path):
    connector, spring, _ = _connector(tmp_path)
    started = connector.start_connection(
        StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
    )
    connector.confirm_tan(started.session_id, "921", "123456")

    fetched = connector.fetch_transactions(started.session_id)
    tx = fetched.accounts[0].transactions[0]

    assert fetched.forwarded_to_backend is True
    assert tx.amount == Decimal("-12.50")
    assert tx.purpose == "Netflix"
    assert tx.counterpart_name == "Netflix International"
    assert tx.counterpart_iban == "DE02120300000000202051"
    assert tx.booking_date == date(2026, 9, 18)
    assert spring.calls[0][0] == started.session_id


def test_fetch_transactions_queries_each_month_separately(tmp_path):
    class RecordingClient(FakeClient):
        def __init__(self) -> None:
            super().__init__()
            self.windows: list[tuple[date | None, date | None]] = []

        def get_transactions(self, account, start_date=None, end_date=None):
            self.windows.append((start_date, end_date))
            return super().get_transactions(account, start_date, end_date)

    client = RecordingClient()
    cipher = CredentialCipher("unit-test-fints-key")
    store = ConnectionStore(cipher, data_dir=str(tmp_path))
    connector = FinTsConnector(
        cipher=cipher,
        store=store,
        client_factory=FakeFactory(client),
        spring_client=FakeSpring(),
        lookback_days=90,
    )
    started = connector.start_connection(
        StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
    )
    connector.confirm_tan(started.session_id, "921", "123456")
    client.windows.clear()
    fetched = connector.fetch_transactions(started.session_id)

    assert len(client.windows) >= 3
    assert client.windows[0][0] <= date.today() - timedelta(days=80)
    assert client.windows[-1][1] == date.today()
    assert len(fetched.accounts[0].transactions) == 1


def test_sync_returns_shared_transaction_format(tmp_path):
    connector, _, _ = _connector(tmp_path)
    accounts = connector.sync("50050201", "user1", "secret-pin")
    assert accounts[0].transactions[0].purpose == "Netflix"
    assert accounts[0].iban == "DE89370400440532013000"


def test_select_tan_method_triggers_pending_challenge(tmp_path):
    class FakeNeedTan:
        challenge = "Bitte in der App bestätigen"
        decoupled = True

        def get_data(self):
            return b"pending-tan"

    class PushClient(FakeClient):
        def get_sepa_accounts(self):
            return FakeNeedTan()

        def pause_dialog(self):
            return b"dialog-state"

    cipher = CredentialCipher("unit-test-fints-key")
    store = ConnectionStore(cipher, data_dir=str(tmp_path))
    connector = FinTsConnector(
        cipher=cipher,
        store=store,
        client_factory=FakeFactory(PushClient()),
        spring_client=FakeSpring(),
    )
    started = connector.start_connection(
        StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
    )
    selected = connector.select_tan_method(started.session_id, "921")

    assert selected.decoupled is True
    assert "App" in (selected.hint or "")
    session = store.get_session(started.session_id)
    assert session is not None
    assert session.pending_tan_ciphertext
    assert session.dialog_state_ciphertext
    assert session.decoupled is True


def test_tan_signature_error_is_not_mapped_as_wrong_pin():
    from services.fints.connector import _map_client_error

    mapped = _map_client_error(
        RuntimeError("Error during dialog initialization, PIN wrong? 9340 Ungültige Signatur."),
        tan_context=True,
    )
    assert mapped.code == "TAN_INVALID"
    assert "Freigabe" in mapped.message


def test_start_maps_dialog_reject_instead_of_swallowing(tmp_path):
    class Boom(FakeClient):
        def fetch_tan_mechanisms(self):
            raise RuntimeError("Error during dialog initialization, could not fetch BPD.")

    cipher = CredentialCipher("unit-test-fints-key")
    store = ConnectionStore(cipher, data_dir=str(tmp_path))
    connector = FinTsConnector(
        cipher=cipher,
        store=store,
        client_factory=FakeFactory(Boom()),
        spring_client=FakeSpring(),
    )
    try:
        connector.start_connection(
            StartConnectionRequest(blz="50050201", loginId="user1", pin="secret-pin")
        )
        assert False, "expected FinTsError"
    except Exception as exc:
        assert getattr(exc, "code", None) == "BANK_UNREACHABLE"
        assert "Anmeldename" in str(exc)


def test_invalid_pin_is_mapped(tmp_path):
    connector, _, _ = _connector(tmp_path, pin="bad")
    try:
        connector.start_connection(
            StartConnectionRequest(blz="50050201", loginId="user1", pin="bad")
        )
        assert False, "expected FinTsError"
    except Exception as exc:
        assert getattr(exc, "code", None) == "INVALID_PIN"


def test_normalize_camt_like_dict():
    tx = normalize_transaction(
        {
            "date": "2026-09-01",
            "amount": Decimal("2800.00"),
            "purpose": "Gehalt",
            "applicant_name": "Arbeitgeber GmbH",
            "applicant_iban": "DE44500105175407324931",
        }
    )
    assert tx.amount == Decimal("2800.00")
    assert tx.booking_date == date(2026, 9, 1)
    assert tx.external_id


def test_cipher_roundtrip_never_equals_plaintext():
    cipher = CredentialCipher("another-test-key")
    token = cipher.encrypt_json({"loginId": "u", "pin": "p4ss"})
    assert "p4ss" not in token
    assert cipher.decrypt_json(token)["pin"] == "p4ss"
