from __future__ import annotations

import re
from datetime import date
from decimal import Decimal
from typing import Optional

from rapidfuzz import fuzz

from models.classification import (
    Classification,
    ClassifyTransactionRequest,
    ClassifyTransactionResponse,
    ExistingContract,
    ExistingIncome,
    ExistingRule,
    HistoryTransaction,
    RuleMatchType,
    RuleTargetType,
    SuggestedAction,
)

STAGE1_IBAN_CONFIDENCE = 0.98
STAGE1_MERCHANT_CONFIDENCE = 0.96
STAGE2_MIN_RATIO = 70.0
STAGE2_CONFIDENCE_MIN = 0.70
STAGE2_CONFIDENCE_MAX = 0.95
STAGE2_AUTO_CALENDAR_MIN = 0.85
AMOUNT_TOLERANCE = Decimal("0.05")
MONTHLY_MIN_DAYS = 25
MONTHLY_MAX_DAYS = 35
STAGE3_BASE_CONFIDENCE = 0.55
STAGE3_MAX_CONFIDENCE = 0.70
STAGE4_CONFIDENCE = 0.25


def normalize_iban(value: Optional[str]) -> str:
    if not value:
        return ""
    return re.sub(r"\s+", "", value).upper()


def normalize_name(value: Optional[str]) -> str:
    if not value:
        return ""
    return re.sub(r"\s+", " ", value).strip().lower()


def amounts_similar(left: Decimal, right: Decimal, tolerance: Decimal = AMOUNT_TOLERANCE) -> bool:
    a = abs(left)
    b = abs(right)
    if b == 0:
        return a == 0
    return abs(a - b) / b <= tolerance


def _merchant_label(request: ClassifyTransactionRequest) -> str:
    return request.counterparty_name or request.purpose or ""


class TransactionClassifier:
    """Vierstufige Klassifikation: Regel → Fuzzy → Wiederkehr → Fallback."""

    def classify(self, request: ClassifyTransactionRequest) -> ClassifyTransactionResponse:
        for stage in (
            self._exact_rule_match,
            self._fuzzy_known_entity_match,
            self._recurring_new_contract,
        ):
            result = stage(request)
            if result is not None:
                return result
        return self._unclassified()

    def _exact_rule_match(
        self, request: ClassifyTransactionRequest
    ) -> Optional[ClassifyTransactionResponse]:
        iban = normalize_iban(request.counterparty_iban)
        merchant = normalize_name(_merchant_label(request))

        for rule in request.existing_rules:
            if self._rule_matches(rule, iban, merchant):
                confidence = (
                    STAGE1_IBAN_CONFIDENCE
                    if rule.match_type == RuleMatchType.IBAN
                    else STAGE1_MERCHANT_CONFIDENCE
                )
                return ClassifyTransactionResponse(
                    classification=_classification_from_target(rule.target_type),
                    confidence=confidence,
                    matched_entity_id=rule.target_entity_id,
                    suggested_action=SuggestedAction.AUTO_CALENDAR,
                )
        return None

    def _rule_matches(self, rule: ExistingRule, iban: str, merchant: str) -> bool:
        if rule.match_type == RuleMatchType.IBAN:
            expected = normalize_iban(rule.match_value)
            return bool(iban) and bool(expected) and iban == expected

        if rule.match_type in (RuleMatchType.MERCHANT_NAME, RuleMatchType.MERCHANT_NAME_FUZZY):
            expected = normalize_name(rule.match_value)
            return bool(merchant) and bool(expected) and merchant == expected

        return False

    def _fuzzy_known_entity_match(
        self, request: ClassifyTransactionRequest
    ) -> Optional[ClassifyTransactionResponse]:
        label = normalize_name(_merchant_label(request))
        if not label:
            return None

        best: Optional[tuple[float, Classification, int]] = None

        for contract in request.existing_contracts:
            candidate = self._score_contract(label, request.amount, contract)
            if candidate and (best is None or candidate[0] > best[0]):
                best = candidate

        for income in request.existing_incomes:
            candidate = self._score_income(label, request.amount, income)
            if candidate and (best is None or candidate[0] > best[0]):
                best = candidate

        if best is None:
            return None

        confidence, classification, entity_id = best
        action = (
            SuggestedAction.AUTO_CALENDAR
            if confidence >= STAGE2_AUTO_CALENDAR_MIN
            else SuggestedAction.ASK_USER
        )
        return ClassifyTransactionResponse(
            classification=classification,
            confidence=confidence,
            matched_entity_id=entity_id,
            suggested_action=action,
        )

    def _score_contract(
        self, label: str, amount: Decimal, contract: ExistingContract
    ) -> Optional[tuple[float, Classification, int]]:
        if contract.monthly_cost is None:
            return None
        if not amounts_similar(amount, contract.monthly_cost):
            return None
        name = contract.counterparty_name or contract.provider
        return self._score_name(label, name, Classification.CONTRACT, contract.id)

    def _score_income(
        self, label: str, amount: Decimal, income: ExistingIncome
    ) -> Optional[tuple[float, Classification, int]]:
        if income.amount is None:
            return None
        if not amounts_similar(amount, income.amount):
            return None
        return self._score_name(label, income.source, Classification.INCOME, income.id)

    def _score_name(
        self, label: str, candidate_name: str, classification: Classification, entity_id: int
    ) -> Optional[tuple[float, Classification, int]]:
        other = normalize_name(candidate_name)
        if not other:
            return None
        ratio = float(fuzz.WRatio(label, other))
        if ratio < STAGE2_MIN_RATIO:
            return None
        return (self._fuzzy_confidence(ratio), classification, entity_id)

    def _fuzzy_confidence(self, ratio: float) -> float:
        scaled = STAGE2_CONFIDENCE_MIN + (ratio - STAGE2_MIN_RATIO) / (
            100.0 - STAGE2_MIN_RATIO
        ) * (STAGE2_CONFIDENCE_MAX - STAGE2_CONFIDENCE_MIN)
        return round(min(STAGE2_CONFIDENCE_MAX, max(STAGE2_CONFIDENCE_MIN, scaled)), 4)

    def _recurring_new_contract(
        self, request: ClassifyTransactionRequest
    ) -> Optional[ClassifyTransactionResponse]:
        iban = normalize_iban(request.counterparty_iban)
        if not iban:
            return None

        matches = [
            item
            for item in request.transaction_history
            if self._is_unassigned_similar(item, iban, request.amount)
        ]
        if len(matches) < 2:
            return None

        dates = sorted({item.booking_date for item in matches} | {request.booking_date})
        if not self._has_monthly_rhythm(dates):
            return None

        return ClassifyTransactionResponse(
            classification=Classification.CONTRACT,
            confidence=self._recurring_confidence(matches, dates),
            matched_entity_id=None,
            suggested_action=SuggestedAction.SUGGEST_NEW_CONTRACT,
        )

    def _is_unassigned_similar(
        self, item: HistoryTransaction, iban: str, amount: Decimal
    ) -> bool:
        if item.linked_contract_id is not None:
            return False
        if normalize_iban(item.counterparty_iban) != iban:
            return False
        return amounts_similar(item.amount, amount)

    def _has_monthly_rhythm(self, dates: list[date]) -> bool:
        if len(dates) < 3:
            return False
        intervals = [(dates[i] - dates[i - 1]).days for i in range(1, len(dates))]
        monthly = [days for days in intervals if MONTHLY_MIN_DAYS <= days <= MONTHLY_MAX_DAYS]
        return len(monthly) >= 2

    def _recurring_confidence(self, matches: list[HistoryTransaction], dates: list[date]) -> float:
        extra = min(len(matches) - 2, 3) * 0.04
        intervals = [(dates[i] - dates[i - 1]).days for i in range(1, len(dates))]
        monthly = [days for days in intervals if MONTHLY_MIN_DAYS <= days <= MONTHLY_MAX_DAYS]
        avg = sum(monthly) / len(monthly) if monthly else 30
        tightness = max(0.0, 1.0 - abs(avg - 30) / 10.0) * 0.05
        return round(min(STAGE3_MAX_CONFIDENCE, STAGE3_BASE_CONFIDENCE + extra + tightness), 4)

    def _unclassified(self) -> ClassifyTransactionResponse:
        return ClassifyTransactionResponse(
            classification=Classification.UNCLASSIFIED,
            confidence=STAGE4_CONFIDENCE,
            matched_entity_id=None,
            suggested_action=SuggestedAction.ASK_USER,
        )


def _classification_from_target(target: RuleTargetType) -> Classification:
    if target == RuleTargetType.INCOME:
        return Classification.INCOME
    return Classification.CONTRACT
