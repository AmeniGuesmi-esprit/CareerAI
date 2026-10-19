import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExperienceService } from './experience.service';
import { ExperienceRequest } from '../models/experience.model';

const REQUEST: ExperienceRequest = {
  poste: 'Développeur', entreprise: 'ACME', lieu: null, typeContrat: 'CDI',
  dateDebut: '2024-01-01', dateFin: null, enCours: true, description: null
};
const BODY = { success: true, message: 'ok', data: null };

describe('ExperienceService', () => {
  let service: ExperienceService;
  let http: HttpTestingController;

  /** Vérifie l'URL (par suffixe) et la méthode, puis renvoie la requête interceptée. */
  const expectReq = (suffix: string, method: string) => {
    const req = http.expectOne(r => r.url.endsWith(suffix));
    expect(req.request.method).toBe(method);
    return req;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ExperienceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll : GET /api/experiences', () => {
    let result: unknown;
    service.getAll().subscribe(r => (result = r));
    expectReq('/api/experiences', 'GET').flush(BODY);
    expect(result).toEqual(BODY);
  });

  it('getById : GET /api/experiences/:id', () => {
    let result: unknown;
    service.getById(7).subscribe(r => (result = r));
    expectReq('/api/experiences/7', 'GET').flush(BODY);
    expect(result).toEqual(BODY);
  });

  it('create : POST /api/experiences avec le corps', () => {
    service.create(REQUEST).subscribe();
    const req = expectReq('/api/experiences', 'POST');
    expect(req.request.body).toEqual(REQUEST);
    req.flush(BODY);
  });

  it('update : PUT /api/experiences/:id avec le corps', () => {
    service.update(7, REQUEST).subscribe();
    const req = expectReq('/api/experiences/7', 'PUT');
    expect(req.request.body).toEqual(REQUEST);
    req.flush(BODY);
  });

  it('delete : DELETE /api/experiences/:id', () => {
    service.delete(7).subscribe();
    expectReq('/api/experiences/7', 'DELETE').flush(BODY);
  });
});
