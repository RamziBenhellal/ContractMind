import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

export interface Bank {
  blz: string;
  name: string;
  bic?: string;
}

export interface TanMethod {
  id: string;
  name: string;
  /** Freitext der Bank, z.B. "Bestätige in deiner chipTAN-App" */
  hint?: string;
}

export interface BankCredentials {
  blz: string;
  loginId: string;
  pin: string;
}

/** Antwort auf POST /bank-connections */
export interface BankConnectionStarted {
  connectionId: string;
  tanMethods: TanMethod[];
}

export interface ConnectedAccount {
  iban: string;
  accountType: string;
  balance: number;
}

export type BankConnectionErrorCode =
  | 'INVALID_PIN'
  | 'TAN_INVALID'
  | 'TAN_EXPIRED'
  | 'BANK_UNREACHABLE'
  | 'SEARCH_FAILED'
  | 'UNKNOWN';

export interface BankConnectionError {
  code: BankConnectionErrorCode;
  message: string;
}

const MESSAGES: Record<BankConnectionErrorCode, string> = {
  INVALID_PIN: 'Login-ID oder PIN stimmen nicht. Bitte prüfe deine Eingaben.',
  TAN_INVALID: 'Die eingegebene TAN ist nicht korrekt. Bitte versuche es erneut.',
  TAN_EXPIRED: 'Die TAN ist abgelaufen. Bitte melde dich erneut an.',
  BANK_UNREACHABLE: 'Deine Bank ist gerade nicht erreichbar. Bitte versuche es später noch einmal.',
  SEARCH_FAILED: 'Die Banksuche ist fehlgeschlagen. Bitte versuche es erneut.',
  UNKNOWN: 'Es ist ein unerwarteter Fehler aufgetreten.',
};

/**
 * Übersetzt eine HTTP-Antwort in einen fachlichen Fehlercode. Bevorzugt wird der
 * Code aus dem Response-Body, damit die Bank den Grund selbst benennen kann.
 */
export function toBankConnectionError(
  error: HttpErrorResponse,
  fallback: BankConnectionErrorCode = 'UNKNOWN'
): BankConnectionError {
  const body = error.error as { code?: string; message?: string } | null;
  const bodyCode = body?.code as BankConnectionErrorCode | undefined;

  if (bodyCode && bodyCode in MESSAGES) {
    return { code: bodyCode, message: body?.message || MESSAGES[bodyCode] };
  }

  // Status 0 heißt: die Anfrage hat den Server nie erreicht (offline, CORS, Timeout)
  if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) {
    return { code: 'BANK_UNREACHABLE', message: MESSAGES.BANK_UNREACHABLE };
  }

  if (error.status === 401 || error.status === 403) {
    return { code: 'INVALID_PIN', message: MESSAGES.INVALID_PIN };
  }

  if (error.status === 410) {
    return { code: 'TAN_EXPIRED', message: MESSAGES.TAN_EXPIRED };
  }

  return { code: fallback, message: MESSAGES[fallback] };
}

@Injectable({
  providedIn: 'root',
})
export class BankConnectionService {
  private apiUrl = 'http://localhost:8080/api';
  private http = inject(HttpClient);

  /** Autocomplete-Suche über Bankname oder BLZ */
  searchBanks(query: string): Observable<Bank[]> {
    return this.http
      .get<Bank[]>(`${this.apiUrl}/banks/search`, { params: { query } })
      .pipe(catchError(error => throwError(() => toBankConnectionError(error, 'SEARCH_FAILED'))));
  }

  startConnection(credentials: BankCredentials): Observable<BankConnectionStarted> {
    return this.http
      .post<BankConnectionStarted>(`${this.apiUrl}/bank-connections`, credentials)
      .pipe(catchError(error => throwError(() => toBankConnectionError(error, 'INVALID_PIN'))));
  }

  confirmTan(connectionId: string, tanMethodId: string, tan: string): Observable<ConnectedAccount[]> {
    return this.http
      .post<ConnectedAccount[]>(`${this.apiUrl}/bank-connections/${connectionId}/confirm-tan`, {
        tanMethodId,
        tan,
      })
      .pipe(catchError(error => throwError(() => toBankConnectionError(error, 'TAN_INVALID'))));
  }
}
