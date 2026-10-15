import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent {
  email = '';
  loading = signal(false);
  error   = signal('');
  emailSent = signal(false);

  constructor(private auth: AuthService) {}

  onSubmit() {
    if (!this.email) {
      this.error.set('Veuillez saisir votre adresse email.');
      return;
    }

    this.loading.set(true);
    this.error.set('');

    this.auth.forgotPassword(this.email).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.success) {
          this.emailSent.set(true);
        } else {
          this.error.set(res.message);
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Une erreur est survenue. Veuillez réessayer.');
      }
    });
  }
}
