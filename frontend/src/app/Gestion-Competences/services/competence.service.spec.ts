import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CompetenceService } from './competence.service';
import { environment } from '../../../environments/environment';
import { CompetenceRequest, FormationRequest } from '../models/competence.model';

describe('CompetenceService', () => {
  let service: CompetenceService;
  let http: HttpTestingController;

  const FORMATIONS = `${environment.apiUrl}/api/formations`;
  const COMPETENCES = `${environment.apiUrl}/api/competences`;

  const formationReq: FormationRequest = {
    diplome: 'Licence SAE', etablissement: 'ESPRIT', domaine: 'Informatique',
    dateDebut: '2023-09-01', dateFin: '2026-06-30', enCours: false, description: null
  };
  const competenceReq: CompetenceRequest = { nom: 'Spring Boot', niveau: 'AVANCE', categorie: 'TECHNIQUE' };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(CompetenceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // ── Formations ──
  it('getFormations → GET /api/formations', () => {
    let result: any;
    service.getFormations().subscribe(r => (result = r));

    const req = http.expectOne(FORMATIONS);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, message: 'ok', data: [] });
    expect(result.success).toBeTrue();
  });

  it('createFormation → POST avec le corps', () => {
    service.createFormation(formationReq).subscribe();

    const req = http.expectOne(FORMATIONS);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(formationReq);
    req.flush({ success: true, message: 'ok', data: {} });
  });

  it('updateFormation → PUT /api/formations/:id', () => {
    service.updateFormation(5, formationReq).subscribe();

    const req = http.expectOne(`${FORMATIONS}/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(formationReq);
    req.flush({ success: true, message: 'ok', data: {} });
  });

  it('deleteFormation → DELETE /api/formations/:id', () => {
    service.deleteFormation(5).subscribe();

    const req = http.expectOne(`${FORMATIONS}/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, message: 'ok', data: null });
  });

  // ── Compétences ──
  it('getCompetences → GET /api/competences', () => {
    service.getCompetences().subscribe();

    const req = http.expectOne(COMPETENCES);
    expect(req.request.method).toBe('GET');
    req.flush({ success: true, message: 'ok', data: [] });
  });

  it('createCompetence → POST avec le corps', () => {
    service.createCompetence(competenceReq).subscribe();

    const req = http.expectOne(COMPETENCES);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(competenceReq);
    req.flush({ success: true, message: 'ok', data: {} });
  });

  it('updateCompetence → PUT /api/competences/:id', () => {
    service.updateCompetence(9, competenceReq).subscribe();

    const req = http.expectOne(`${COMPETENCES}/9`);
    expect(req.request.method).toBe('PUT');
    req.flush({ success: true, message: 'ok', data: {} });
  });

  it('deleteCompetence → DELETE /api/competences/:id', () => {
    service.deleteCompetence(9).subscribe();

    const req = http.expectOne(`${COMPETENCES}/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ success: true, message: 'ok', data: null });
  });

  it('propage les erreurs HTTP', () => {
    let status = 0;
    service.getCompetences().subscribe({ error: e => (status = e.status) });

    http.expectOne(COMPETENCES).flush({ message: 'nope' }, { status: 500, statusText: 'Server Error' });
    expect(status).toBe(500);
  });
});
