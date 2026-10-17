import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OrientationService } from './orientation.service';
import { environment } from '../../../environments/environment';

describe('OrientationService', () => {
  let service: OrientationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(OrientationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('advise → POST /api/orientation/advice avec { prompt }', () => {
    let result: any;
    service.advise('Quelles certifications pour le cloud ?').subscribe(r => (result = r));

    const req = http.expectOne(`${environment.apiUrl}/api/orientation/advice`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ prompt: 'Quelles certifications pour le cloud ?' });

    req.flush({ success: true, message: 'Conseil généré', data: { reponse: 'x', statut: 'VERIFIEE', avertissement: null } });
    expect(result.data.statut).toBe('VERIFIEE');
  });

  it('propage une erreur 429 (quota)', () => {
    let status = 0;
    service.advise('un prompt suffisamment long').subscribe({ error: e => (status = e.status) });

    http.expectOne(`${environment.apiUrl}/api/orientation/advice`)
      .flush({ message: 'Trop de demandes' }, { status: 429, statusText: 'Too Many Requests' });
    expect(status).toBe(429);
  });
});
