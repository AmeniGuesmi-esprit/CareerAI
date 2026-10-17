import { Routes } from '@angular/router';

/**
 * Routes du module "Analyse de compatibilité CV / offre".
 * Montées sous /frontoffice/compatibility (voir app-routing.module.ts).
 * L'ordre compte : 'new' doit précéder ':id'.
 */
export const COMPATIBILITY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./analyse-history/analyse-history.component').then(c => c.AnalyseHistoryComponent)
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./analyse-form/analyse-form.component').then(c => c.AnalyseFormComponent)
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./analyse-result/analyse-result.component').then(c => c.AnalyseResultComponent)
  }
];