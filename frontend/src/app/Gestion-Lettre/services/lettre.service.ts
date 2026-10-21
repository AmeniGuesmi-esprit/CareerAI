import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../Gestion-Utilisateur/models/user.model';
import {
  Lettre, LettreRequest,
  AiProvider, AiGenerateRequest, AiImproveRequest, AiLetterResult
} from '../models/lettre.model';

/** Appels vers le lettre-service (via l'API Gateway). Le token JWT est ajouté par authInterceptor. */
@Injectable({ providedIn: 'root' })
export class LettreService {
  private readonly API = `${environment.apiUrl}/api/lettres`;

  constructor(private http: HttpClient) {}

  // ── Utilisateur connecté : ses propres lettres ──

  getMine(): Observable<ApiResponse<Lettre[]>> {
    return this.http.get<ApiResponse<Lettre[]>>(this.API);
  }

  getById(id: number): Observable<ApiResponse<Lettre>> {
    return this.http.get<ApiResponse<Lettre>>(`${this.API}/${id}`);
  }

  create(request: LettreRequest): Observable<ApiResponse<Lettre>> {
    return this.http.post<ApiResponse<Lettre>>(this.API, request);
  }

  update(id: number, request: LettreRequest): Observable<ApiResponse<Lettre>> {
    return this.http.put<ApiResponse<Lettre>>(`${this.API}/${id}`, request);
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/${id}`);
  }

  // ── Assistant IA (n'enregistre rien : on enregistre ensuite avec create / update) ──

  aiProviders(): Observable<ApiResponse<AiProvider[]>> {
    return this.http.get<ApiResponse<AiProvider[]>>(`${this.API}/ai/providers`);
  }

  /** Envoie un CV (PDF, DOCX, TXT) et récupère son texte. */
  aiExtractCv(file: File): Observable<ApiResponse<string>> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ApiResponse<string>>(`${this.API}/ai/extract-cv`, form);
  }

  aiGenerate(request: AiGenerateRequest): Observable<ApiResponse<AiLetterResult>> {
    return this.http.post<ApiResponse<AiLetterResult>>(`${this.API}/ai/generate`, request);
  }

  aiImprove(request: AiImproveRequest): Observable<ApiResponse<AiLetterResult>> {
    return this.http.post<ApiResponse<AiLetterResult>>(`${this.API}/ai/improve`, request);
  }

  // ── ADMIN ──

  adminGetAll(): Observable<ApiResponse<Lettre[]>> {
    return this.http.get<ApiResponse<Lettre[]>>(`${this.API}/admin/all`);
  }

  adminDelete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/admin/${id}`);
  }
}