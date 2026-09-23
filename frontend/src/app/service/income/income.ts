import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';


export interface Income{
  id?:number;
  amount?:number;
  source?:string;
  paydayOfMonth?: number;
}

@Injectable({
  providedIn: 'root',
})
export class IncomeService {
  private apiUrl = 'http://localhost:8080/api/incomes';
  private http = inject(HttpClient)

  getIncomes():Observable<Income[]> {
    return this.http.get<Income[]>(this.apiUrl);
  }

  addIncome(incomeData: Income): Observable<Income>{
    return this.http.post<Income>(`${this.apiUrl}/add`,incomeData);
  }

  updateIncome(id: number, incomeData: Income): Observable<Income>{
      return this.http.put<Income> (`${this.apiUrl}/${id}`,incomeData);
    }

    deleteIncome(id: number): Observable<void> {
      return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}
