import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../../Gestion-Utilisateur/models/user.model';
import { CvUpload, SkillGapAnalysis } from '../models/skill-gap.model';

/** Appels au skill-gap-service, via l'API Gateway (le JWT est ajouté par l'intercepteur). */
@Injectable({ providedIn: 'root' })
export class SkillGapService {
  private readonly API = `${environment.apiUrl}/api/skill-gap`;

  constructor(private http: HttpClient) {}

  uploadCv(file: File): Observable<ApiResponse<CvUpload>> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ApiResponse<CvUpload>>(`${this.API}/cvs`, form);
  }

  getMyCvs(): Observable<ApiResponse<CvUpload[]>> {
    return this.http.get<ApiResponse<CvUpload[]>>(`${this.API}/cvs`);
  }

  deleteCv(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/cvs/${id}`);
  }

  analyze(cvId: number, jobTitle: string, jobDescription: string): Observable<ApiResponse<SkillGapAnalysis>> {
    return this.http.post<ApiResponse<SkillGapAnalysis>>(`${this.API}/analyses`, { cvId, jobTitle, jobDescription });
  }

  getMyAnalyses(): Observable<ApiResponse<SkillGapAnalysis[]>> {
    return this.http.get<ApiResponse<SkillGapAnalysis[]>>(`${this.API}/analyses`);
  }

  deleteAnalysis(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/analyses/${id}`);
  }
}
