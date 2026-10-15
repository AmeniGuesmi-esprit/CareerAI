import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  if (token) req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

  return next(req).pipe(
    catchError(err => {
      // Token expiré / invalide : on déconnecte (sauf pour les routes publiques d'auth)
      if (err.status === 401 && token && !req.url.includes('/api/users/auth/')) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
