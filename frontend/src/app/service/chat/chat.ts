import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ChatService {
  private http = inject(HttpClient)
  private apiURL = 'http://localhost:8080/api/chat'

  askQuestion(question:string): Observable<{ answer: string }>{
    return this.http.post<{ answer: string }>(`${this.apiURL}/ask`, { question });
  }
}
