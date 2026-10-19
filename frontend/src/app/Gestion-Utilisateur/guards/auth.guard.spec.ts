import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { adminGuard, authGuard, guestGuard, homeRoute } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('auth guards', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  const run = (guard: any) =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn', 'getRole']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }, { provide: Router, useValue: router }]
    });
  });

  describe('homeRoute', () => {
    it('ADMIN → backoffice', () => expect(homeRoute('ADMIN')).toBe('/backoffice/dashboard'));
    it('USER → frontoffice', () => expect(homeRoute('USER')).toBe('/frontoffice/home'));
    it('null → frontoffice', () => expect(homeRoute(null)).toBe('/frontoffice/home'));
  });

  describe('authGuard', () => {
    it('laisse passer un utilisateur connecté', () => {
      auth.isLoggedIn.and.returnValue(true);
      expect(run(authGuard)).toBeTrue();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('redirige vers /login sinon', () => {
      auth.isLoggedIn.and.returnValue(false);
      expect(run(authGuard)).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('adminGuard', () => {
    it('laisse passer un ADMIN', () => {
      auth.getRole.and.returnValue('ADMIN');
      expect(run(adminGuard)).toBeTrue();
    });

    it('renvoie un USER vers le frontoffice', () => {
      auth.getRole.and.returnValue('USER');
      expect(run(adminGuard)).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/frontoffice/home']);
    });
  });

  describe('guestGuard', () => {
    it('laisse passer un visiteur non connecté', () => {
      auth.isLoggedIn.and.returnValue(false);
      expect(run(guestGuard)).toBeTrue();
    });

    it('renvoie un ADMIN connecté vers son dashboard', () => {
      auth.isLoggedIn.and.returnValue(true);
      auth.getRole.and.returnValue('ADMIN');
      expect(run(guestGuard)).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/backoffice/dashboard']);
    });

    it('renvoie un USER connecté vers le frontoffice', () => {
      auth.isLoggedIn.and.returnValue(true);
      auth.getRole.and.returnValue('USER');
      run(guestGuard);
      expect(router.navigate).toHaveBeenCalledWith(['/frontoffice/home']);
    });
  });
});
