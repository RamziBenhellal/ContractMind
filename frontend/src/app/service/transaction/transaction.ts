import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type FeedbackDecision = 'EXISTING_MATCH' | 'NEW_CONTRACT' | 'NEW_INCOME' | 'NORMAL';

export interface PendingReviewTransaction {
  id: number;
  amount: number;
  purpose?: string;
  counterpartyName?: string;
  counterpartyIban?: string;
  bookingDate?: string;
  classification?: string;
  confidenceScore?: number;
  suggestNewContract?: boolean;
}

export interface NewContractData {
  provider?: string;
  contractType?: string;
  monthlyCost?: number;
  dueDayOfMonth?: number;
}

export interface TransactionFeedbackRequest {
  decision: FeedbackDecision;
  targetEntityId?: number | null;
  newContractData?: NewContractData | null;
  category?: string | null;
}

export interface TransactionFeedbackResponse {
  id: number;
  classification: string;
  classificationStatus: string;
  linkedContractId?: number | null;
  linkedIncomeId?: number | null;
  category?: string | null;
  suggestNewContract: boolean;
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly apiUrl = 'http://localhost:8080/api/transactions';
  private readonly http = inject(HttpClient);

  getPendingReview(): Observable<PendingReviewTransaction[]> {
    return this.http.get<PendingReviewTransaction[]>(`${this.apiUrl}/pending-review`);
  }

  submitFeedback(id: number, body: TransactionFeedbackRequest): Observable<TransactionFeedbackResponse> {
    return this.http.post<TransactionFeedbackResponse>(`${this.apiUrl}/${id}/feedback`, body);
  }
}
