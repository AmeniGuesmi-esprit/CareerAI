import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../services/auth.service';
import { User } from '../models/user.model';

/**
 * Page "Mon profil" — partagée par le frontoffice (USER) et le backoffice (ADMIN).
 * Données issues de GET /api/users/me ; mise à jour via PUT /me ; suppression via DELETE /me.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  user: User | null = null;

  // Champs du formulaire de modification
  prenom = '';
  nom = '';
  telephone = '';
  adresse = '';
  password = '';

  editMode = false;
  showDeleteConfirm = false;

  loading = signal(false);
  success = signal('');
  error = signal('');

  constructor(private auth: AuthService) {}

  ngOnInit() {
    // Affichage immédiat depuis la session, puis rafraîchissement depuis le serveur
    this.setUser(this.auth.getCurrentUser());

    this.auth.getMe().subscribe({
      next: (res) => {
        if (res.success && res.data) this.setUser(res.data);
      },
      error: () => this.error.set('Impossible de rafraîchir le profil depuis le serveur.')
    });
  }

  get isAdmin(): boolean {
    return this.user?.role === 'ADMIN';
  }

  get initials(): string {
    const p = this.user?.prenom?.trim().charAt(0) ?? '';
    const n = this.user?.nom?.trim().charAt(0) ?? '';
    return `${p}${n}`.toUpperCase() || '?';
  }

  private setUser(user: User | null) {
    this.user = user;
    if (user) this.resetForm(user);
  }

  private resetForm(user: User) {
    this.prenom = user.prenom;
    this.nom = user.nom;
    this.telephone = user.telephone || '';
    this.adresse = user.adresse || '';
    this.password = '';
  }

  openEdit() {
    this.editMode = true;
    this.success.set('');
    this.error.set('');
  }

  closeEdit() {
    this.editMode = false;
    if (this.user) this.resetForm(this.user);
  }

  onUpdate() {
    if (!this.user) return;
    this.error.set('');
    this.success.set('');

    if (!this.prenom.trim() || !this.nom.trim() || !this.telephone.trim() || !this.adresse.trim()) {
      this.error.set('Tous les champs sont obligatoires');
      return;
    }
    if (!/^[+0-9 ()-]{8,20}$/.test(this.telephone.trim())) {
      this.error.set('Numéro de téléphone invalide');
      return;
    }
    if (this.password.trim() && this.password.length < 8) {
      this.error.set('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    this.loading.set(true);
    this.auth.updateMe({
      prenom: this.prenom.trim(),
      nom: this.nom.trim(),
      telephone: this.telephone.trim(),
      adresse: this.adresse.trim(),
      ...(this.password.trim() ? { motDePasse: this.password } : {})
    }).subscribe({
      next: (res) => {
        this.loading.set(false);
        if (res.success && res.data) {
          this.setUser(res.data);
          this.success.set('✅ Profil mis à jour avec succès !');
          this.editMode = false;
        } else {
          this.error.set(res.message || 'Erreur lors de la mise à jour');
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Erreur serveur');
      }
    });
  }

  confirmDelete() { this.showDeleteConfirm = true; }

  deleteAccount() {
    this.auth.deleteMe().subscribe({
      next: (res) => {
        if (res.success) {
          this.auth.logout();
        } else {
          // ex. : dernier administrateur
          this.showDeleteConfirm = false;
          this.error.set(res.message);
        }
      },
      error: () => this.showDeleteConfirm = false
    });
  }
}
