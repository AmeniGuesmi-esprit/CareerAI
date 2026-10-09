import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="display:flex; justify-content:center; align-items:center;
                height:100vh; flex-direction:column; font-family:sans-serif;
                background:#f5f5f5;">
      <div style="background:white; padding:48px; border-radius:16px;
                  box-shadow:0 4px 24px rgba(0,0,0,0.1); text-align:center;
                  max-width:400px; width:90%;">

        <!-- Loading -->
        <ng-container *ngIf="loading">
          <p style="font-size:48px; margin:0;">⏳</p>
          <h2 style="color:#6a1b9a; margin-top:16px;">Vérification en cours...</h2>
          <p style="color:#666;">Veuillez patienter.</p>
        </ng-container>

        <!-- Succès -->
        <ng-container *ngIf="!loading && success">
          <p style="font-size:48px; margin:0;">✅</p>
          <h2 style="color:#6a1b9a; margin-top:16px;">Email vérifié !</h2>
          <p style="color:#666;">
            Votre compte est activé.<br>Redirection vers la connexion dans
            <strong>{{ countdown }}s</strong>...
          </p>
          <button (click)="goToLogin()"
            style="margin-top:24px; padding:12px 32px; background:#6a1b9a;
                   color:white; border:none; border-radius:8px; cursor:pointer;
                   font-size:16px; width:100%;">
            Se connecter maintenant
          </button>
        </ng-container>

        <!-- Erreur -->
        <ng-container *ngIf="!loading && !success">
          <p style="font-size:48px; margin:0;">❌</p>
          <h2 style="color:#e53935; margin-top:16px;">Échec de la vérification</h2>
          <p style="color:#666;">{{ errorMsg }}</p>
          <button (click)="goToLogin()"
            style="margin-top:24px; padding:12px 32px; background:#6a1b9a;
                   color:white; border:none; border-radius:8px; cursor:pointer;
                   font-size:16px; width:100%;">
            Retour à la connexion
          </button>
        </ng-container>

      </div>
    </div>
  `
})
export class VerifyEmailComponent implements OnInit {
  loading = true;
  success = false;
  errorMsg = '';
  countdown = 3;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.loading = false;
      this.errorMsg = 'Token manquant dans le lien.';
      return;
    }

    this.auth.verifyEmail(token).subscribe({
      next: (res) => {
        this.loading = false;
        this.success = res.success === true;
        this.cdr.markForCheck();
        if (this.success) {
          // Redirection automatique après 3 secondes
          const interval = setInterval(() => {
            this.countdown--;
            this.cdr.markForCheck();
            if (this.countdown === 0) {
              clearInterval(interval);
              this.goToLogin();
            }
          }, 1000);
        } else {
          this.errorMsg = res.message || 'Vérification échouée.';
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = 'Erreur lors de la vérification. Le lien est peut-être expiré.';
        this.cdr.markForCheck();
      }
    });
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }
}