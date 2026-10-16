import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminComponent } from './theme/layout/admin/admin.component';
import { GuestComponent } from './theme/layout/guest/guest.component';
import { authGuard, adminGuard, guestGuard } from './Gestion-Utilisateur/guards/auth.guard';
import { FrontofficeLayoutComponent } from './frontoffice/layout/frontoffice-layout.component';

const routes: Routes = [

  // LANDING PAGE
  {
    path: '',
    loadComponent: () =>
      import('./landing/landing.component').then(c => c.LandingComponent),
    pathMatch: 'full'
  },

  // Lien reçu par email après l'inscription
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./Gestion-Utilisateur/verify-email/verify-email.component').then(c => c.VerifyEmailComponent)
  },

  // Mot de passe oublié + lien de réinitialisation reçu par email
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./Gestion-Utilisateur/forgot-password/forgot-password.component').then(c => c.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./Gestion-Utilisateur/reset-password/reset-password.component').then(c => c.ResetPasswordComponent)
  },

  // Auth / Guest
  {
    path: '',
    component: GuestComponent,
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./Gestion-Utilisateur/login/login.component').then(c => c.LoginComponent)
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () =>
          import('./Gestion-Utilisateur/register/register.component').then(c => c.RegisterComponent)
      }
    ]
  },

  // Backoffice - ADMIN
  {
    path: 'backoffice',
    component: AdminComponent,
    canActivate: [authGuard, adminGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./backoffice/dashboard/admin-dashboard.component').then(c => c.AdminDashboardComponent)
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./Gestion-Utilisateur/users/users-list.component').then(c => c.UsersListComponent)
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./Gestion-Utilisateur/profile/profile.component').then(c => c.ProfileComponent)
      }
    ]
  },

  // Frontoffice - USER
  // Les futurs modules (CV, lettre de motivation, matching, entretien) s'ajoutent ici.
  {
    path: 'frontoffice',
    component: FrontofficeLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      {
        path: 'home',
        loadComponent: () =>
          import('./frontoffice/home/home.component').then(c => c.HomeComponent)
      },

      // Gestion des expériences
      {
        path: 'experiences',
        loadComponent: () =>
          import('./Gestion-Experience/experience-list/experience-list.component').then(c => c.ExperienceListComponent)
      },
      {
        path: 'experiences/new',
        loadComponent: () =>
          import('./Gestion-Experience/experience-form/experience-form.component').then(c => c.ExperienceFormComponent)
      },
      {
        path: 'experiences/:id/edit',
        loadComponent: () =>
          import('./Gestion-Experience/experience-form/experience-form.component').then(c => c.ExperienceFormComponent)
      },

      // Pitch de 30 secondes
      {
        path: 'pitch',
        loadComponent: () =>
          import('./Gestion-Experience/pitch/pitch.component').then(c => c.PitchComponent)
      },

      {
        path: 'profile',
        loadComponent: () =>
          import('./Gestion-Utilisateur/profile/profile.component').then(c => c.ProfileComponent)
      }
    ]
  },

  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}