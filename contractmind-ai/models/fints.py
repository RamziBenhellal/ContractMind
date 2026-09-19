from datetime import date
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class BankInfo(CamelModel):
    blz: str
    name: str
    bic: Optional[str] = None
    fints_url: Optional[str] = Field(default=None, alias="fintsUrl")


class TanMethod(CamelModel):
    id: str
    name: str
    hint: Optional[str] = None


class StartConnectionRequest(CamelModel):
    blz: Optional[str] = None
    bic: Optional[str] = None
    login_id: str = Field(alias="loginId")
    pin: str
    fints_url: Optional[str] = Field(default=None, alias="fintsUrl")


class StartConnectionResponse(CamelModel):
    connection_id: str = Field(alias="connectionId")
    session_id: str = Field(alias="sessionId")
    bank_name: Optional[str] = Field(default=None, alias="bankName")
    tan_methods: list[TanMethod] = Field(default_factory=list, alias="tanMethods")


class ConfirmTanRequest(CamelModel):
    connection_id: Optional[str] = Field(default=None, alias="connectionId")
    tan_method_id: Optional[str] = Field(default=None, alias="tanMethodId")
    tan: str = ""


class SelectTanMethodRequest(CamelModel):
    tan_method_id: str = Field(alias="tanMethodId")


class SelectTanMethodResponse(CamelModel):
    hint: Optional[str] = None
    decoupled: bool = False
    challenge: Optional[str] = None


class FinTsAccount(CamelModel):
    iban: Optional[str] = None
    account_type: Optional[str] = Field(default=None, alias="accountType")
    balance: Optional[Decimal] = None
    bank_name: Optional[str] = Field(default=None, alias="bankName")


class ConfirmTanResponse(CamelModel):
    connection_id: str = Field(alias="connectionId")
    accounts: list[FinTsAccount] = Field(default_factory=list)


class FinTsTransaction(CamelModel):
    external_id: Optional[str] = Field(default=None, alias="externalId")
    booking_date: Optional[date] = Field(default=None, alias="bookingDate")
    amount: Decimal
    purpose: Optional[str] = None
    counterpart_name: Optional[str] = Field(default=None, alias="counterpartName")
    counterpart_iban: Optional[str] = Field(default=None, alias="counterpartIban")


class SyncedAccount(FinTsAccount):
    transactions: list[FinTsTransaction] = Field(default_factory=list)


class SyncRequest(CamelModel):
    blz: str
    login_id: str = Field(alias="loginId")
    pin: str
    fints_url: Optional[str] = Field(default=None, alias="fintsUrl")


class SyncResponse(CamelModel):
    accounts: list[SyncedAccount] = Field(default_factory=list)


class TransactionFetchResponse(CamelModel):
    connection_id: str = Field(alias="connectionId")
    accounts: list[SyncedAccount] = Field(default_factory=list)
    forwarded_to_backend: bool = Field(alias="forwardedToBackend")
