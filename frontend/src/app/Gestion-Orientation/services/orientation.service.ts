import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../Gestion-Competences/models/competence.model';
import { Advice } from '../models/orientation.model';

/**
 * Conseiller d'orientation (Gemini) : toujours via l'API Gateway.
 * Le token JWT est ajouté automatiquement par authInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class OrientationService {
  private readonly API = `${environment.apiUrl}/api/orientation`;

  constructor(private http: HttpClient) {}

  advise(prompt: string): Observable<ApiResponse<Advice>> {
    return this.http.post<ApiResponse<Advice>>(`${this.API}/advice`, { prompt });
  }
}
