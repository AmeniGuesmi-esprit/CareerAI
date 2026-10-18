import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse, Competence, CompetenceRequest, Formation, FormationRequest
} from '../models/competence.model';

/**
 * Appels vers le competence-service, TOUJOURS via l'API Gateway (environment.apiUrl).
 * Le token JWT est ajouté automatiquement par authInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class CompetenceService {
  private readonly FORMATIONS = `${environment.apiUrl}/api/formations`;
  private readonly COMPETENCES = `${environment.apiUrl}/api/competences`;

  constructor(private http: HttpClient) {}

  // ── Formations ───────────────────────────────────────────────────────────────
  getFormations(): Observable<ApiResponse<Formation[]>> {
    return this.http.get<ApiResponse<Formation[]>>(this.FORMATIONS);
  }
  createFormation(req: FormationRequest): Observable<ApiResponse<Formation>> {
    return this.http.post<ApiResponse<Formation>>(this.FORMATIONS, req);
  }
  updateFormation(id: number, req: FormationRequest): Observable<ApiResponse<Formation>> {
    return this.http.put<ApiResponse<Formation>>(`${this.FORMATIONS}/${id}`, req);
  }
  deleteFormation(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.FORMATIONS}/${id}`);
  }

  // ── Compétences ──────────────────────────────────────────────────────────────
  getCompetences(): Observable<ApiResponse<Competence[]>> {
    return this.http.get<ApiResponse<Competence[]>>(this.COMPETENCES);
  }
  createCompetence(req: CompetenceRequest): Observable<ApiResponse<Competence>> {
    return this.http.post<ApiResponse<Competence>>(this.COMPETENCES, req);
  }
  updateCompetence(id: number, req: CompetenceRequest): Observable<ApiResponse<Competence>> {
    return this.http.put<ApiResponse<Competence>>(`${this.COMPETENCES}/${id}`, req);
  }
  deleteCompetence(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.COMPETENCES}/${id}`);
  }
}
