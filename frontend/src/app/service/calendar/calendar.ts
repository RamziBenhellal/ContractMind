import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type CalendarEntryType = 'CONTRACT_PAYMENT' | 'INCOME';

export interface CalendarOccurrence {
  sourceEntryId?: number;
  title: string;
  date: string;
  amount: number;
  type: CalendarEntryType;
  recurrenceRule?: string;
  sourceContractId?: number | null;
  sourceIncomeId?: number | null;
  occurred: boolean;
  matchedTransactionId?: number | null;
}

export interface AvailableBalance {
  currentBalance: number;
  expectedIncome: number;
  outstandingCharges: number;
  availableBalance: number;
  month: string;
}

@Injectable({ providedIn: 'root' })
export class CalendarService {
  private readonly apiUrl = 'http://localhost:8080/api/calendar';
  private readonly http = inject(HttpClient);

  getMonth(month: string): Observable<CalendarOccurrence[]> {
    return this.http.get<CalendarOccurrence[]>(this.apiUrl, { params: { month } });
  }

  getAvailableBalance(): Observable<AvailableBalance> {
    return this.http.get<AvailableBalance>(`${this.apiUrl}/available-balance`);
  }
}
