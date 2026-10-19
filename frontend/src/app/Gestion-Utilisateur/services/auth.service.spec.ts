import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { User } from '../models/user.model';

describe('AuthService', () => {
  const API = `${environment.apiUrl}/api/users`;
  const user: User = {
    id: 1, nom: 'Chakroun', prenom: 'Yesmine', email: 'y@x.com', telephone: '1', adresse: 'Tunis',
    role: 'USER', emailVerified: true, enabled: true
  };
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  function create(): AuthService {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [{ provide: Router, useValue: router }]
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => { http?.verify(); localStorage.clear(); });

  describe('session au démarrage', () => {
    it('restaure un utilisateur valide depuis localStorage', () => {
      localStorage.setItem('user', JSON.stringify(user));
      localStorage.setItem('token', 't');
      const s = create();

      expect(s.getCurrentUser()).toEqual(user);
      expect(s.isLoggedIn()).toBeTrue();
      expect(s.getRole()).toBe('USER');
    });

    it('nettoie une session à l\'ancien format', () => {
      localStorage.setItem('user', JSON.stringify({ firstName: 'x', role: 'CANDIDATE' }));
      localStorage.setItem('token', 't');
      const s = create();

      expect(s.getCurrentUser()).toBeNull();
      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
    });

    it('nettoie un JSON invalide', () => {
      localStorage.setItem('user', '{pas du json');
      const s = create();

      expect(s.getCurrentUser()).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
    });

    it('isLoggedIn faux sans token', () => {
      localStorage.setItem('user', JSON.stringify(user));
      expect(create().isLoggedIn()).toBeFalse();
    });
  });

  describe('login', () => {
    it('stocke token + utilisateur et notifie user$', () => {
      const s = create();
      let emitted: User | null | undefined;
      s.user$.subscribe(u => { emitted = u; });

      s.login('y@x.com', 'pwd').subscribe();
      const req = http.expectOne(`${API}/auth/login`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'y@x.com', motDePasse: 'pwd' });
      req.flush({ success: true, message: 'ok', data: { token: 'jwt', user } });

      expect(localStorage.getItem('token')).toBe('jwt');
      expect(JSON.parse(localStorage.getItem('user')!)).toEqual(user);
      expect(emitted).toEqual(user);
      expect(s.isLoggedIn()).toBeTrue();
    });

    it('ne stocke rien si success=false', () => {
      const s = create();
      s.login('y@x.com', 'bad').subscribe();
      http.expectOne(`${API}/auth/login`).flush({ success: false, message: 'ko', data: null });

      expect(localStorage.getItem('token')).toBeNull();
      expect(s.isLoggedIn()).toBeFalse();
    });
  });

  describe('endpoints d\'authentification', () => {
    it('register → POST /auth/register', () => {
      const s = create();
      const body = { nom: 'a', prenom: 'b', email: 'c@d.e', telephone: '1', adresse: 'x', motDePasse: 'p' };
      s.register(body).subscribe();
      const req = http.expectOne(`${API}/auth/register`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({ success: true, message: '', data: null });
    });

    it('verifyEmail → GET avec le token en paramètre', () => {
      const s = create();
      s.verifyEmail('tok').subscribe();
      const req = http.expectOne(r => r.url === `${API}/auth/verify-email`);
      expect(req.request.params.get('token')).toBe('tok');
      req.flush({ success: true, message: '', data: null });
    });

    it('forgotPassword → POST { email }', () => {
      const s = create();
      s.forgotPassword('a@b.c').subscribe();
      const req = http.expectOne(`${API}/auth/forgot-password`);
      expect(req.request.body).toEqual({ email: 'a@b.c' });
      req.flush({ success: true, message: '', data: null });
    });

    it('resetPassword → POST { token, nouveauMotDePasse }', () => {
      const s = create();
      s.resetPassword('tok', 'new').subscribe();
      const req = http.expectOne(`${API}/auth/reset-password`);
      expect(req.request.body).toEqual({ token: 'tok', nouveauMotDePasse: 'new' });
      req.flush({ success: true, message: '', data: null });
    });
  });

  describe('profil', () => {
    it('getMe met à jour l\'utilisateur courant', () => {
      const s = create();
      s.getMe().subscribe();
      http.expectOne(`${API}/me`).flush({ success: true, message: '', data: user });

      expect(s.getCurrentUser()).toEqual(user);
    });

    it('updateMe → PUT /me puis met à jour l\'utilisateur', () => {
      const s = create();
      const updated = { ...user, prenom: 'Y2' };
      s.updateMe({ nom: 'Chakroun', prenom: 'Y2', telephone: '1', adresse: 'Tunis' }).subscribe();
      const req = http.expectOne(`${API}/me`);
      expect(req.request.method).toBe('PUT');
      req.flush({ success: true, message: '', data: updated });

      expect(s.getCurrentUser()?.prenom).toBe('Y2');
    });

    it('deleteMe → DELETE /me', () => {
      const s = create();
      s.deleteMe().subscribe();
      const req = http.expectOne(`${API}/me`);
      expect(req.request.method).toBe('DELETE');
      req.flush({ success: true, message: '', data: null });
    });
  });

  it('logout vide la session et redirige vers /login', () => {
    localStorage.setItem('user', JSON.stringify(user));
    localStorage.setItem('token', 't');
    const s = create();

    s.logout();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(s.getCurrentUser()).toBeNull();
    expect(s.getRole()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
