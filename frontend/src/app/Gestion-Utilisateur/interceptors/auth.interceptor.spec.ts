import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  function setup(token: string | null) {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getToken', 'logout']);
    auth.getToken.and.returnValue(token);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth }
      ]
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  }

  afterEach(() => ctrl.verify());

  it('ajoute l\'en-tête Bearer quand un token existe', () => {
    setup('abc123');
    http.get('/api/competences').subscribe();

    const req = ctrl.expectOne('/api/competences');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc123');
    req.flush({});
  });

  it('n\'ajoute rien sans token', () => {
    setup(null);
    http.get('/api/competences').subscribe();

    const req = ctrl.expectOne('/api/competences');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('401 avec token sur une route protégée → logout et erreur propagée', () => {
    setup('abc123');
    let status = 0;
    http.get('/api/formations').subscribe({ error: e => (status = e.status) });

    ctrl.expectOne('/api/formations').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(status).toBe(401);
  });

  it('401 sur une route d\'auth publique → pas de logout', () => {
    setup('abc123');
    http.post('/api/users/auth/login', {}).subscribe({ error: () => {} });

    ctrl.expectOne('/api/users/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('401 sans token → pas de logout', () => {
    setup(null);
    http.get('/api/formations').subscribe({ error: () => {} });

    ctrl.expectOne('/api/formations').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('les autres erreurs (500) ne déconnectent pas', () => {
    setup('abc123');
    http.get('/api/formations').subscribe({ error: () => {} });

    ctrl.expectOne('/api/formations').flush({}, { status: 500, statusText: 'Server Error' });
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
