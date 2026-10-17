import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../Gestion-Utilisateur/services/auth.service';
import { AnalysisListItem, AnalysisResponse, Page } from './compatibility.models';

/** Transforme une erreur HTTP en message lisible pour l'utilisateur. */
export function apiErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Impossible de joindre le serveur. Vérifiez que la gateway (port 8222) et le compatibility-service sont démarrés.';
    }
    const message = err.error?.message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    switch (err.status) {
      case 401: return 'Session expirée : reconnectez-vous.';
      case 404: return 'Ressource introuvable.';
      case 502:
      case 503:
      case 504: return 'Le service est momentanément indisponible. Réessayez dans un instant.';
      default: return `Erreur inattendue (HTTP ${err.status}).`;
    }
  }
  return 'Une erreur inattendue est survenue.';
}

@Injectable({ providedIn: 'root' })
export class CompatibilityService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  /** Toutes les requêtes passent par la gateway (port 8222). */
  private readonly API = `${environment.apiUrl}/api/compatibility/analyses`;

  /**
   * Le compatibility-service identifie l'utilisateur via l'en-tête X-User-Id.
   * Limite connue : à terme, la gateway devra le déduire du JWT validé.
   */
  private headers(): HttpHeaders {
    const id = this.auth.getCurrentUser()?.id ?? 1;
    return new HttpHeaders({ 'X-User-Id': String(id) });
  }

  /** CREATE : upload du CV + offre → analyse IA. Ne pas fixer Content-Type (le navigateur ajoute le boundary). */
  create(cv: File, jobTitle: string, jobDescription: string): Observable<AnalysisResponse> {
    const form = new FormData();
    form.append('cv', cv, cv.name);
    form.append('jobTitle', jobTitle);
    form.append('jobDescription', jobDescription);
    return this.http.post<AnalysisResponse>(this.API, form, { headers: this.headers() });
  }

  /** READ : historique paginé (plus récent en premier). */
  list(page = 0, size = 10): Observable<Page<AnalysisListItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<AnalysisListItem>>(this.API, { headers: this.headers(), params });
  }

  /** READ : détail. */
  get(id: number): Observable<AnalysisResponse> {
    return this.http.get<AnalysisResponse>(`${this.API}/${id}`, { headers: this.headers() });
  }

  /** UPDATE : renommer l'intitulé du poste. */
  rename(id: number, jobTitle: string): Observable<AnalysisResponse> {
    return this.http.patch<AnalysisResponse>(`${this.API}/${id}`, { jobTitle }, { headers: this.headers() });
  }

  /** DELETE */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`, { headers: this.headers() });
  }
}