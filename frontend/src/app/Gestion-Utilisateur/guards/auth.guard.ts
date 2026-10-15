import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Page d'accueil après connexion : backoffice pour l'ADMIN, frontoffice pour l'USER. */
export function homeRoute(role: string | null): string {
  return role === 'ADMIN' ? '/backoffice/dashboard' : '/frontoffice/home';
}

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService); const router = inject(Router);
  if (!auth.isLoggedIn()) { router.navigate(['/login']); return false; }
  return true;
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService); const router = inject(Router);
  if (auth.getRole() !== 'ADMIN') { router.navigate([homeRoute(auth.getRole())]); return false; }
  return true;
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService); const router = inject(Router);
  if (auth.isLoggedIn()) { router.navigate([homeRoute(auth.getRole())]); return false; }
  return true;
};