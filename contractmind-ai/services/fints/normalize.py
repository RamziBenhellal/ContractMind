from __future__ import annotations

import hashlib
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Any

from models.fints import FinTsTransaction


def normalize_transaction(raw: Any) -> FinTsTransaction:
    data = _as_dict(raw)
    amount = _to_decimal(
        data.get("amount")
        or getattr(raw, "amount", None)
        or data.get("transaction_amount")
    )
    booking = _to_date(
        data.get("date")
        or data.get("entry_date")
        or data.get("booking_date")
        or getattr(raw, "date", None)
    )
    purpose = _first_text(
        data.get("purpose"),
        data.get("transaction_details"),
        data.get("posting_text"),
        getattr(raw, "purpose", None),
    )
    name = _first_text(
        data.get("applicant_name"),
        data.get("counterpart_name"),
        data.get("name"),
        getattr(raw, "applicant_name", None),
    )
    iban = _first_text(
        data.get("applicant_iban"),
        data.get("counterpart_iban"),
        data.get("iban"),
        getattr(raw, "applicant_iban", None),
    )
    external_id = _first_text(data.get("id"), data.get("external_id"), getattr(raw, "id", None))
    if not external_id:
        external_id = _derive_external_id(booking, amount, purpose, iban)

    return FinTsTransaction(
        external_id=external_id,
        booking_date=booking,
        amount=amount,
        purpose=purpose,
        counterpart_name=name,
        counterpart_iban=iban,
    )


def _as_dict(raw: Any) -> dict[str, Any]:
    if isinstance(raw, dict):
        return raw
    data = getattr(raw, "data", None)
    if isinstance(data, dict):
        return data
    if hasattr(raw, "__dict__"):
        return {key: value for key, value in vars(raw).items() if not key.startswith("_")}
    return {}


def _to_decimal(value: Any) -> Decimal:
    if value is None:
        return Decimal("0")
    amount = getattr(value, "amount", value)
    try:
        return Decimal(str(amount))
    except (InvalidOperation, TypeError, ValueError):
        return Decimal("0")


def _to_date(value: Any) -> date | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    text = str(value)
    for fmt in ("%Y-%m-%d", "%Y%m%d", "%d.%m.%Y"):
        try:
            return datetime.strptime(text[:10] if fmt == "%Y-%m-%d" else text, fmt).date()
        except ValueError:
            continue
    return None


def _first_text(*values: Any) -> str | None:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def _derive_external_id(booking: date | None, amount: Decimal, purpose: str | None, iban: str | None) -> str:
    raw = "|".join(
        [
            booking.isoformat() if booking else "",
            str(amount),
            purpose or "",
            iban or "",
        ]
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
