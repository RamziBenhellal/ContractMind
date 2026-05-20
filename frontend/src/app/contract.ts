import { Injectable, inject } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

export interface Contract {
  id?: number;
  provider: string;
  contractType: string;
  monthlyCost: number;
  endDate: string;
  status?:string
}

@Injectable({
  providedIn: 'root',
})


export class ContractService {
  private apiURL = 'http://localhost:8080/api/contracts';
  private http = inject(HttpClient);

  getContracts(): Observable<Contract[]> {
    return this.http.get<Contract[]>(this.apiURL);
  }

  addContract(contractData: Contract): Observable<Contract> {
    return this.http.post<Contract>(`${this.apiURL}/add`, contractData);
  }

  uploadContract(file: File): Observable<any>{
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post(`${this.apiURL}/upload`, formData);
  }

  updateContract(id: number, contractData: Contract): Observable<Contract>{
    return this.http.put<Contract> (`${this.apiURL}/${id}`,contractData);
  }

  deleteContract(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiURL}/${id}`);
  }
}
