import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { NavbarComponent } from '../../theme/shared/navbar/navbar.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, NavbarComponent],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {
  prenom = '';
  nom = '';
  email = '';
  telephone = '';
  adresse = '';
  password = '';

  loading = signal(false);
  error = signal('');
  success = signal('');

  constructor(private auth: AuthService) {}

  onSubmit() {
    this.error.set('');
    this.success.set('');

    if (!this.prenom.trim() || !this.nom.trim() || !this.email.trim()
        || !this.telephone.trim() || !this.adresse.trim() || !this.password) {
      this.error.set('Veuillez remplir tous les champs obligatoires');
      return;
    }
    // Même règle que le backend : chiffres, +, espaces, (), - ; 8 à 20 caractères
    if (!/^[+0-9 ()-]{8,20}$/.test(this.telephone.trim())) {
      this.error.set('Numéro de téléphone invalide');
      return;
    }
    if (this.password.length < 8) {
      this.error.set('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    this.loading.set(true);
    this.auth.register({
      nom: this.nom.trim(),
      prenom: this.prenom.trim(),
      email: this.email.trim(),
      telephone: this.telephone.trim(),
      adresse: this.adresse.trim(),
      motDePasse: this.password
    }).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.success) {
          this.success.set('Inscription réussie ! Vérifiez votre email pour activer votre compte.');
        } else {
          this.error.set(res.message);
        }
      },
      error: (err) => {
        this.loading.set(false);
        // Erreurs de validation renvoyées par le backend (GlobalExceptionHandler)
        this.error.set(err?.error?.message || 'Erreur lors de l\'inscription. Veuillez réessayer.');
      }
    });
  }
}
