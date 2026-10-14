import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Experience, ExperienceRequest } from '../models/experience.model';

/**
 * Appels vers l'experience-service, via l'API Gateway (environment.apiUrl = http://localhost:8222).
 * Le token JWT est ajouté automatiquement par authInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class ExperienceService {
  private readonly API = `${environment.apiUrl}/api/experiences`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<ApiResponse<Experience[]>> {
    return this.http.get<ApiResponse<Experience[]>>(this.API);
  }

  getById(id: number): Observable<ApiResponse<Experience>> {
    return this.http.get<ApiResponse<Experience>>(`${this.API}/${id}`);
  }

  create(request: ExperienceRequest): Observable<ApiResponse<Experience>> {
    return this.http.post<ApiResponse<Experience>>(this.API, request);
  }

  update(id: number, request: ExperienceRequest): Observable<ApiResponse<Experience>> {
    return this.http.put<ApiResponse<Experience>>(`${this.API}/${id}`, request);
  }

  delete(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/${id}`);
  }
}
