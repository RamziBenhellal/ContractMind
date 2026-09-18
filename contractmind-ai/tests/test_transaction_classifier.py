from datetime import date
from decimal import Decimal

from models.classification import (
    Classification,
    ClassifyTransactionRequest,
    ExistingContract,
    ExistingIncome,
    ExistingRule,
    HistoryTransaction,
    RuleMatchType,
    RuleTargetType,
    SuggestedAction,
)
from services.transaction_classifier import TransactionClassifier

classifier = TransactionClassifier()


def _request(**overrides) -> ClassifyTransactionRequest:
    payload = {
        "amount": Decimal("-12.50"),
        "purpose": "Netflix",
        "counterparty_name": "Netflix International",
        "counterparty_iban": "DE02120300000000202051",
        "booking_date": date(2026, 9, 18),
    }
    payload.update(overrides)
    return ClassifyTransactionRequest(**payload)


def test_stage1_iban_exact_match_auto_calendar():
    result = classifier.classify(
        _request(
            existing_rules=[
                ExistingRule(
                    match_type=RuleMatchType.IBAN,
                    match_value="DE02 1203 0000 0000 2020 51",
                    target_type=RuleTargetType.CONTRACT,
                    target_entity_id=42,
                )
            ],
            existing_contracts=[
                ExistingContract(id=42, provider="Netflix", monthly_cost=Decimal("12.50"))
            ],
        )
    )

    assert result.classification == Classification.CONTRACT
    assert result.matched_entity_id == 42
    assert result.confidence >= 0.95
    assert result.suggested_action == SuggestedAction.AUTO_CALENDAR


def test_stage1_merchant_exact_match_classifies_income():
    result = classifier.classify(
        _request(
            amount=Decimal("2800.00"),
            purpose="Gehalt September",
            counterparty_name="Acme GmbH",
            counterparty_iban="DE44500105175407324931",
            existing_rules=[
                ExistingRule(
                    match_type=RuleMatchType.MERCHANT_NAME,
                    match_value="  ACME gmbh ",
                    target_type=RuleTargetType.INCOME,
                    target_entity_id=7,
                )
            ],
        )
    )

    assert result.classification == Classification.INCOME
    assert result.matched_entity_id == 7
    assert result.confidence >= 0.95
    assert result.suggested_action == SuggestedAction.AUTO_CALENDAR


def test_stage1_does_not_match_similar_but_different_merchant():
    result = classifier.classify(
        _request(
            counterparty_name="Netflix International",
            existing_rules=[
                ExistingRule(
                    match_type=RuleMatchType.MERCHANT_NAME_FUZZY,
                    match_value="Netflx",
                    target_type=RuleTargetType.CONTRACT,
                    target_entity_id=42,
                )
            ],
        )
    )

    assert result.matched_entity_id != 42
    assert result.confidence < 0.95


def test_stage1_empty_iban_does_not_match():
    result = classifier.classify(
        _request(
            counterparty_iban=None,
            existing_rules=[
                ExistingRule(
                    match_type=RuleMatchType.IBAN,
                    match_value="",
                    target_type=RuleTargetType.CONTRACT,
                    target_entity_id=1,
                )
            ],
        )
    )

    assert result.classification == Classification.UNCLASSIFIED
    assert result.confidence < 0.5


def test_stage2_fuzzy_contract_name_with_amount_tolerance():
    result = classifier.classify(
        _request(
            amount=Decimal("-12.99"),
            counterparty_name="Netflix Intl. B.V.",
            existing_contracts=[
                ExistingContract(id=11, provider="Netflix", monthly_cost=Decimal("12.50"))
            ],
        )
    )

    assert result.classification == Classification.CONTRACT
    assert result.matched_entity_id == 11
    assert 0.70 <= result.confidence <= 0.95
    assert result.suggested_action in {
        SuggestedAction.AUTO_CALENDAR,
        SuggestedAction.ASK_USER,
    }


def test_stage2_rejects_amount_outside_five_percent():
    result = classifier.classify(
        _request(
            amount=Decimal("-20.00"),
            counterparty_name="Netflix International",
            existing_contracts=[
                ExistingContract(id=11, provider="Netflix", monthly_cost=Decimal("12.50"))
            ],
        )
    )

    assert result.classification == Classification.UNCLASSIFIED
    assert result.matched_entity_id is None
    assert result.confidence < 0.5


def test_stage2_fuzzy_income_match():
    result = classifier.classify(
        _request(
            amount=Decimal("2750.00"),
            purpose="Lohn",
            counterparty_name="ACME Gehaltsabrechnung",
            existing_incomes=[
                ExistingIncome(id=3, source="Acme GmbH", amount=Decimal("2800.00"))
            ],
        )
    )

    assert result.classification == Classification.INCOME
    assert result.matched_entity_id == 3
    assert 0.70 <= result.confidence <= 0.95


def test_stage2_high_similarity_uses_auto_calendar():
    result = classifier.classify(
        _request(
            amount=Decimal("-39.99"),
            counterparty_name="Vodafone GmbH",
            existing_contracts=[
                ExistingContract(id=5, provider="Vodafone GmbH", monthly_cost=Decimal("39.99"))
            ],
        )
    )

    assert result.classification == Classification.CONTRACT
    assert result.confidence >= 0.85
    assert result.suggested_action == SuggestedAction.AUTO_CALENDAR


def test_stage3_suggests_new_contract_for_monthly_unassigned_iban():
    iban = "DE89370400440532013000"
    result = classifier.classify(
        _request(
            amount=Decimal("-49.00"),
            purpose="Fitness First",
            counterparty_name="Fitness First",
            counterparty_iban=iban,
            booking_date=date(2026, 9, 1),
            transaction_history=[
                HistoryTransaction(
                    amount=Decimal("-49.00"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 7, 2),
                ),
                HistoryTransaction(
                    amount=Decimal("-48.50"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 8, 1),
                ),
            ],
        )
    )

    assert result.classification == Classification.CONTRACT
    assert result.matched_entity_id is None
    assert 0.5 <= result.confidence <= 0.7
    assert result.suggested_action == SuggestedAction.SUGGEST_NEW_CONTRACT


def test_stage3_skips_history_already_linked_to_a_contract():
    iban = "DE89370400440532013000"
    result = classifier.classify(
        _request(
            amount=Decimal("-49.00"),
            counterparty_name="Fitness First",
            counterparty_iban=iban,
            booking_date=date(2026, 9, 1),
            transaction_history=[
                HistoryTransaction(
                    amount=Decimal("-49.00"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 7, 2),
                    linked_contract_id=99,
                ),
                HistoryTransaction(
                    amount=Decimal("-49.00"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 8, 1),
                    linked_contract_id=99,
                ),
            ],
        )
    )

    assert result.classification == Classification.UNCLASSIFIED
    assert result.suggested_action == SuggestedAction.ASK_USER
    assert result.confidence < 0.5


def test_stage3_skips_when_rhythm_is_not_monthly():
    iban = "DE89370400440532013000"
    result = classifier.classify(
        _request(
            amount=Decimal("-49.00"),
            counterparty_name="Fitness First",
            counterparty_iban=iban,
            booking_date=date(2026, 9, 1),
            transaction_history=[
                HistoryTransaction(
                    amount=Decimal("-49.00"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 3, 1),
                ),
                HistoryTransaction(
                    amount=Decimal("-49.00"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 6, 15),
                ),
            ],
        )
    )

    assert result.classification == Classification.UNCLASSIFIED
    assert result.confidence < 0.5


def test_stage4_unclassified_fallback():
    result = classifier.classify(
        _request(
            amount=Decimal("-4.20"),
            purpose="REWE SAGT DANKE",
            counterparty_name="REWE Markt",
            counterparty_iban="DE12500105170648489890",
        )
    )

    assert result.classification == Classification.UNCLASSIFIED
    assert result.matched_entity_id is None
    assert result.confidence < 0.5
    assert result.suggested_action == SuggestedAction.ASK_USER


def test_request_accepts_camel_case_payload():
    request = ClassifyTransactionRequest.model_validate(
        {
            "amount": "-12.50",
            "purpose": "Netflix",
            "counterpartyName": "Netflix International",
            "counterpartyIban": "DE02120300000000202051",
            "bookingDate": "2026-09-18",
            "existing_contracts": [{"id": 1, "provider": "Netflix", "monthlyCost": "12.50"}],
            "existing_rules": [
                {
                    "matchType": "IBAN",
                    "matchValue": "DE02120300000000202051",
                    "targetType": "CONTRACT",
                    "targetEntityId": 1,
                }
            ],
        }
    )
    result = classifier.classify(request)
    dumped = result.model_dump(by_alias=True)

    assert dumped["classification"] == "CONTRACT"
    assert dumped["matchedEntityId"] == 1
    assert dumped["suggestedAction"] == "AUTO_CALENDAR"


def test_stage_order_exact_rule_beats_fuzzy_and_recurring():
    iban = "DE02120300000000202051"
    result = classifier.classify(
        _request(
            existing_rules=[
                ExistingRule(
                    match_type=RuleMatchType.IBAN,
                    match_value=iban,
                    target_type=RuleTargetType.CONTRACT,
                    target_entity_id=1,
                )
            ],
            existing_contracts=[
                ExistingContract(id=99, provider="Netflix International", monthly_cost=Decimal("12.50"))
            ],
            transaction_history=[
                HistoryTransaction(
                    amount=Decimal("-12.50"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 7, 18),
                ),
                HistoryTransaction(
                    amount=Decimal("-12.50"),
                    counterparty_iban=iban,
                    booking_date=date(2026, 8, 18),
                ),
            ],
        )
    )

    assert result.matched_entity_id == 1
    assert result.confidence >= 0.95
    assert result.suggested_action == SuggestedAction.AUTO_CALENDAR
