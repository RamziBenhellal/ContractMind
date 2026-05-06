import { Injectable, inject } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, tap} from 'rxjs';

export interface AuthResponse {
  token: string;
  email: string;
  message: string;
}

@Injectable({
  providedIn: 'root',
})

export class AuthService {
  private apiURL= 'http://localhost:8080/api/auth';
  private http = inject(HttpClient);

  register(userData: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiURL}/register`, userData);
  }

  login(credentials: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiURL}/login`, credentials).pipe(
      tap(response =>{
        if(response.token){
          localStorage.setItem('jwt_token',response.token);
          localStorage.setItem('email',response.email);
        }
      })
    )
  }
  logout():void {
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('email');
  }
}
