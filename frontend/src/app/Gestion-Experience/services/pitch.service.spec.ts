import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PitchService } from './pitch.service';

describe('PitchService', () => {
  let service: PitchService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PitchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('generate : POST /api/pitch avec { experiences }', () => {
    const body = { success: true, message: 'ok', data: { courte: 'a', standard: 'b', enthousiaste: 'c' } };
    let result: unknown;

    service.generate({ experiences: 'Stage chez ACME' }).subscribe(r => (result = r));

    const req = http.expectOne(r => r.url.endsWith('/api/pitch'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ experiences: 'Stage chez ACME' });
    req.flush(body);
    expect(result).toEqual(body);
  });
});
