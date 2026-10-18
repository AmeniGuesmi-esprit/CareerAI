import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnswerView, CreateSessionRequest, CvSummary, KnowledgeOverview, KnowledgeSearchRequest, KnowledgeSearchResult,
  Page, RagView, SessionDetail, SessionSummary
} from './models/interview.model';

@Injectable({ providedIn: 'root' })
export class InterviewService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/api/interview`;

  listCvs(page = 0, size = 20): Observable<Page<CvSummary>> {
    return this.http.get<Page<CvSummary>>(`${this.API}/cvs`, { params: { page, size } });
  }

  uploadCv(file: File, label?: string): Observable<CvSummary> {
    const body = new FormData();
    body.append('file', file);
    if (label?.trim()) body.append('label', label.trim());
    return this.http.post<CvSummary>(`${this.API}/cvs`, body);
  }

  deleteCv(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/cvs/${id}`);
  }

  listSessions(page = 0, size = 20): Observable<Page<SessionSummary>> {
    return this.http.get<Page<SessionSummary>>(`${this.API}/sessions`, { params: { page, size } });
  }

  getSession(id: number): Observable<SessionDetail> {
    return this.http.get<SessionDetail>(`${this.API}/sessions/${id}`);
  }

  getRag(id: number): Observable<RagView> {
    return this.http.get<RagView>(`${this.API}/sessions/${id}/rag`);
  }

  knowledgeOverview(): Observable<KnowledgeOverview> {
    return this.http.get<KnowledgeOverview>(`${this.API}/knowledge`);
  }

  knowledgeSearch(request: KnowledgeSearchRequest): Observable<KnowledgeSearchResult> {
    return this.http.post<KnowledgeSearchResult>(`${this.API}/knowledge/search`, request);
  }

  createSession(request: CreateSessionRequest): Observable<SessionDetail> {
    return this.http.post<SessionDetail>(`${this.API}/sessions`, request);
  }

  renameSession(id: number, jobTitle: string): Observable<SessionDetail> {
    return this.http.patch<SessionDetail>(`${this.API}/sessions/${id}`, { jobTitle });
  }

  deleteSession(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/sessions/${id}`);
  }

  retrySession(id: number): Observable<SessionDetail> {
    return this.http.post<SessionDetail>(`${this.API}/sessions/${id}/retry`, {});
  }

  saveAnswer(sessionId: number, questionId: number, content: string): Observable<AnswerView> {
    return this.http.put<AnswerView>(
      `${this.API}/sessions/${sessionId}/questions/${questionId}/answer`, { content });
  }

  deleteAnswer(sessionId: number, questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/sessions/${sessionId}/questions/${questionId}/answer`);
  }
}
