import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import {
  ApiResponse, AuthResponse, RegisterRequest, Role, UpdateProfileRequest, User
} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = `${environment.apiUrl}/api/users`;

  userSubject = new BehaviorSubject<User | null>(this.loadUserFromStorage());
  user$ = this.userSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

  /** Ignore (et nettoie) une session issue de l'ancien format (firstName/CANDIDATE...) */
  private loadUserFromStorage(): User | null {
    try {
      const raw = localStorage.getItem('user');
      const user = raw ? JSON.parse(raw) : null;
      if (user && typeof user.prenom === 'string' && (user.role === 'ADMIN' || user.role === 'USER')) {
        return user;
      }
    } catch { /* JSON invalide */ }
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return null;
  }

  private saveUser(user: User): void {
    localStorage.setItem('user', JSON.stringify(user));
    this.userSubject.next(user);
  }

  // ── Authentification ─────────────────────────────────────────────────────────

  login(email: string, motDePasse: string): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API}/auth/login`, { email, motDePasse }).pipe(
      tap(res => {
        if (res.success && res.data) {
          localStorage.setItem('token', res.data.token);
          this.saveUser(res.data.user);
        }
      })
    );
  }

  register(request: RegisterRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/auth/register`, request);
  }

  verifyEmail(token: string): Observable<ApiResponse<void>> {
    return this.http.get<ApiResponse<void>>(`${this.API}/auth/verify-email`, { params: { token } });
  }

  /** Étape 1 : soumet l'email → le backend envoie le lien par email */
  forgotPassword(email: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/auth/forgot-password`, { email });
  }

  /** Étape 2 : soumet le token + nouveau mot de passe */
  resetPassword(token: string, nouveauMotDePasse: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/auth/reset-password`, { token, nouveauMotDePasse });
  }

  // ── Profil (utilisateur connecté) ────────────────────────────────────────────

  getMe(): Observable<ApiResponse<User>> {
    return this.http.get<ApiResponse<User>>(`${this.API}/me`).pipe(
      tap(res => { if (res.success && res.data) this.saveUser(res.data); })
    );
  }

  updateMe(request: UpdateProfileRequest): Observable<ApiResponse<User>> {
    return this.http.put<ApiResponse<User>>(`${this.API}/me`, request).pipe(
      tap(res => { if (res.success && res.data) this.saveUser(res.data); })
    );
  }

  deleteMe(): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/me`);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null { return localStorage.getItem('token'); }
  getCurrentUser(): User | null { return this.userSubject.getValue(); }
  isLoggedIn(): boolean { return !!this.getToken() && !!this.getCurrentUser(); }
  getRole(): Role | null { return this.getCurrentUser()?.role ?? null; }
}
