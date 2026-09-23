import { HttpClient } from '@angular/common/http';
import { InjectionToken, inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

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

export interface ActualTransaction {
  id: number;
  accountId: number;
  accountName: string;
  amount: number;
  purpose?: string;
  counterpartyName?: string;
  linkedContractId?: number | null;
  linkedIncomeId?: number | null;
}

export interface AccountTransactionGroup {
  accountId: number;
  accountName: string;
  iban?: string;
  transactions: ActualTransaction[];
  actualBalance: number;
}

export interface AccountBalance {
  accountId: number;
  accountName: string;
  iban?: string;
  actualBalance: number;
}

export interface HistoricalDay {
  date: string;
  actualTransactions: AccountTransactionGroup[];
  actualBalance: number;
  balancesByAccount: AccountBalance[];
  /** Verträge/Einkommen an diesem Tag (Anzeige, ändert actualBalance nicht). */
  plannedEntries?: CalendarOccurrence[];
}

export interface ProjectedDay {
  date: string;
  projectedEntries: CalendarOccurrence[];
  projectedBalance: number;
}

export interface CalendarMonthView {
  month: string;
  today: string;
  historicalDays: HistoricalDay[];
  projectedDays: ProjectedDay[];
}

export function toIsoDate(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Jackson kann LocalDate als ISO-String oder als [Jahr, Monat, Tag] liefern. */
export function toDayKey(value: unknown): string {
  if (value == null) {
    return '';
  }
  if (typeof value === 'string') {
    return value.length >= 10 ? value.slice(0, 10) : value;
  }
  if (Array.isArray(value) && value.length >= 2) {
    const year = Number(value[0]);
    const month = Number(value[1]);
    const day = value.length >= 3 ? Number(value[2]) : 1;
    if (!Number.isFinite(year) || !Number.isFinite(month)) {
      return '';
    }
    const iso = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return value.length >= 3 ? iso : iso.slice(0, 7);
  }
  if (typeof value === 'object') {
    const record = value as { year?: number; month?: number; day?: number };
    if (record.year != null && record.month != null) {
      const iso = `${record.year}-${String(record.month).padStart(2, '0')}-${String(record.day ?? 1).padStart(2, '0')}`;
      return record.day != null ? iso : iso.slice(0, 7);
    }
  }
  return String(value);
}

function normalizeOccurrence(entry: CalendarOccurrence): CalendarOccurrence {
  return { ...entry, date: toDayKey(entry.date) };
}

function normalizeHistoricalDay(day: HistoricalDay): HistoricalDay {
  return {
    ...day,
    date: toDayKey(day.date),
    plannedEntries: (day.plannedEntries ?? []).map(normalizeOccurrence),
  };
}

function normalizeProjectedDay(day: ProjectedDay): ProjectedDay {
  return {
    ...day,
    date: toDayKey(day.date),
    projectedEntries: (day.projectedEntries ?? []).map(normalizeOccurrence),
  };
}

function normalizeMonthView(view: CalendarMonthView): CalendarMonthView {
  const month = toDayKey(view.month);
  return {
    ...view,
    month: month.length >= 7 ? month.slice(0, 7) : String(view.month ?? ''),
    today: toDayKey(view.today),
    historicalDays: (view.historicalDays ?? []).map(normalizeHistoricalDay),
    projectedDays: (view.projectedDays ?? []).map(normalizeProjectedDay),
  };
}

/** Liefert das lokale Kalenderdatum, damit Ist/Prognose nach Mitternacht neu berechnet werden. */
export const CALENDAR_TODAY = new InjectionToken<() => string>('CALENDAR_TODAY', {
  providedIn: 'root',
  factory: () => () => toIsoDate(),
});

@Injectable({ providedIn: 'root' })
export class CalendarService {
  private readonly apiUrl = 'http://localhost:8080/api/calendar';
  private readonly http = inject(HttpClient);

  getMonth(month: string): Observable<CalendarOccurrence[]> {
    return this.http.get<CalendarOccurrence[]>(this.apiUrl, { params: { month } });
  }

  getMonthView(month: string): Observable<CalendarMonthView> {
    return this.http
      .get<CalendarMonthView>(`${this.apiUrl}/month-view`, { params: { month } })
      .pipe(map(normalizeMonthView));
  }

  getDayView(date: string): Observable<HistoricalDay> {
    return this.http
      .get<HistoricalDay>(`${this.apiUrl}/day-view`, { params: { date } })
      .pipe(map(day => ({ ...normalizeHistoricalDay(day), date: toDayKey(day.date) || date })));
  }

  getAvailableBalance(): Observable<AvailableBalance> {
    return this.http.get<AvailableBalance>(`${this.apiUrl}/available-balance`);
  }
}
