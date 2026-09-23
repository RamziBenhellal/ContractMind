from fastapi import APIRouter

from models.classification import ClassifyTransactionRequest, ClassifyTransactionResponse
from services.transaction_classifier import TransactionClassifier

router = APIRouter(tags=["Classification"])
classifier = TransactionClassifier()


@router.post("/classify-transaction", response_model=ClassifyTransactionResponse)
def classify_transaction(payload: ClassifyTransactionRequest) -> ClassifyTransactionResponse:
    return classifier.classify(payload)
