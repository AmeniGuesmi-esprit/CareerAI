import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {
  newPassword     = '';
  confirmPassword = '';
  showPassword    = false;
  showConfirm     = false;

  loading      = signal(false);
  error        = signal('');
  resetDone    = signal(false);
  tokenInvalid = signal(false);

  private token = '';

  constructor(
    private auth: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.tokenInvalid.set(true);
    }
  }

  onSubmit() {
    this.error.set('');

    if (!this.newPassword || !this.confirmPassword) {
      this.error.set('Veuillez remplir les deux champs.');
      return;
    }
    if (this.newPassword.length < 8) {
      this.error.set('Le mot de passe doit contenir au moins 8 caractères.');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('Les mots de passe ne correspondent pas.');
      return;
    }

    this.loading.set(true);

    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.success) {
          this.resetDone.set(true);
          // Redirection automatique vers login après 2 secondes
          setTimeout(() => this.router.navigate(['/login']), 2000);
        } else {
          // Token expiré ou invalide selon réponse backend
          if (res.message?.toLowerCase().includes('expiré') || res.message?.toLowerCase().includes('invalide')) {
            this.tokenInvalid.set(true);
          } else {
            this.error.set(res.message);
          }
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Une erreur est survenue. Veuillez réessayer.');
      }
    });
  }
}
