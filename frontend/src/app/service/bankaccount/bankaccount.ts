import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

export interface BankAccount {
  id?: number;
  accountName?: string;
  bankName?: string;
  lastSyncedAt?: string | null;
  balanceHistory?: Record<string,number>
}

export interface SyncNowResponse {
  syncedAt: string;
  newTransactionCount: number;
  updatedBalance: number;
}
@Injectable({
  providedIn: 'root',
})
export class BankAccountService {
  private apiUrl = 'http://localhost:8080/api/bank-accounts';
  private http = inject(HttpClient);

  getBankAccounts():Observable<BankAccount[]>{
    return this.http.get<BankAccount[]>(this.apiUrl);
  }

  addBankAccount(bankAccount: BankAccount):Observable<BankAccount>{
    return this.http.post<BankAccount>(`${this.apiUrl}/add`,bankAccount);
  }

  updateBankAcount(id: number, bankAccount: BankAccount):Observable<BankAccount>{
    return this.http.put<BankAccount>(`${this.apiUrl}/${id}`,bankAccount);
  }

  recordBalance(id:number, date: string, balance: number): Observable<BankAccount>{
    const payload = {
      date: date,
      balance: balance
    };
    return this.http.put<BankAccount>
    (`${this.apiUrl}/${id}/record-balance`,payload);
  }

  deleteBankAccount(id:number):Observable<void>{
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  syncNow(id: number): Observable<SyncNowResponse> {
    return this.http.post<SyncNowResponse>(`${this.apiUrl}/${id}/sync-now`, {});
  }
}
