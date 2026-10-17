import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/experience.model';
import { PitchRequest, PitchResponse } from '../models/pitch.model';

/**
 * Appel vers l'experience-service, via l'API Gateway (environment.apiUrl = http://localhost:8222).
 * Le token JWT est ajouté automatiquement par authInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class PitchService {
  private readonly API = `${environment.apiUrl}/api/pitch`;

  constructor(private http: HttpClient) {}

  generate(request: PitchRequest): Observable<ApiResponse<PitchResponse>> {
    return this.http.post<ApiResponse<PitchResponse>>(this.API, request);
  }
}