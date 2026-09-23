from datetime import date
from decimal import Decimal
from enum import Enum
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class Classification(str, Enum):
    CONTRACT = "CONTRACT"
    INCOME = "INCOME"
    NORMAL = "NORMAL"
    UNCLASSIFIED = "UNCLASSIFIED"


class SuggestedAction(str, Enum):
    AUTO_CALENDAR = "AUTO_CALENDAR"
    ASK_USER = "ASK_USER"
    SUGGEST_NEW_CONTRACT = "SUGGEST_NEW_CONTRACT"


class RuleMatchType(str, Enum):
    IBAN = "IBAN"
    MERCHANT_NAME_FUZZY = "MERCHANT_NAME_FUZZY"
    MERCHANT_NAME = "MERCHANT_NAME"
    AMOUNT_RANGE = "AMOUNT_RANGE"


class RuleTargetType(str, Enum):
    CONTRACT = "CONTRACT"
    INCOME = "INCOME"


class ExistingContract(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    provider: str
    monthly_cost: Optional[Decimal] = Field(default=None, alias="monthlyCost")
    counterparty_iban: Optional[str] = Field(default=None, alias="counterpartyIban")
    counterparty_name: Optional[str] = Field(default=None, alias="counterpartyName")


class ExistingIncome(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    source: str
    amount: Optional[Decimal] = None


class ExistingRule(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    match_type: RuleMatchType = Field(alias="matchType")
    match_value: str = Field(alias="matchValue")
    target_type: RuleTargetType = Field(alias="targetType")
    target_entity_id: int = Field(alias="targetEntityId")


class HistoryTransaction(BaseModel):
    """Vorherige Umsätze, nötig für die Wiederkehr-Heuristik (Stufe 3)."""

    model_config = ConfigDict(populate_by_name=True)

    amount: Decimal
    counterparty_iban: Optional[str] = Field(default=None, alias="counterpartyIban")
    booking_date: date = Field(alias="bookingDate")
    linked_contract_id: Optional[int] = Field(default=None, alias="linkedContractId")


class ClassifyTransactionRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    amount: Decimal
    purpose: Optional[str] = None
    counterparty_name: Optional[str] = Field(default=None, alias="counterpartyName")
    counterparty_iban: Optional[str] = Field(default=None, alias="counterpartyIban")
    booking_date: date = Field(alias="bookingDate")
    existing_contracts: list[ExistingContract] = Field(default_factory=list)
    existing_incomes: list[ExistingIncome] = Field(default_factory=list)
    existing_rules: list[ExistingRule] = Field(default_factory=list)
    transaction_history: list[HistoryTransaction] = Field(
        default_factory=list, alias="transactionHistory"
    )


class ClassifyTransactionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    classification: Classification
    confidence: float = Field(ge=0.0, le=1.0)
    matched_entity_id: Optional[int] = Field(default=None, alias="matchedEntityId")
    suggested_action: SuggestedAction = Field(alias="suggestedAction")
