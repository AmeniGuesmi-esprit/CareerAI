import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PitchService } from '../services/pitch.service';
import { PITCH_TABS, PitchResponse, PitchTab } from '../models/pitch.model';

/**
 * Page "Pitch de 30 secondes" (frontoffice USER) : l'utilisateur décrit ses expériences,
 * l'IA génère 3 versions (courte / standard / enthousiaste) affichées en onglets, avec bouton Copier.
 */
@Component({
  selector: 'app-pitch',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pitch.component.html',
  styleUrls: ['./pitch.component.scss']
})
export class PitchComponent {
  readonly tabs = PITCH_TABS;

  experiences = signal('');
  loading = signal(false);
  error = signal('');
  pitch = signal<PitchResponse | null>(null);
  activeTab = signal<PitchTab>('standard');
  copied = signal<PitchTab | ''>('');

  constructor(private service: PitchService) {}

  generate() {
    const text = this.experiences().trim();
    if (!text) {
      this.error.set('Décrivez vos expériences avant de générer le pitch.');
      return;
    }
    if (this.loading()) return;

    this.loading.set(true);
    this.error.set('');
    this.pitch.set(null);

    this.service.generate({ experiences: text }).subscribe({
      next: res => {
        this.pitch.set(res.data ?? null);
        this.activeTab.set('standard');
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(this.messageFor(err));
        this.loading.set(false);
      }
    });
  }

  selectTab(tab: PitchTab) {
    this.activeTab.set(tab);
  }

  textOf(tab: PitchTab): string {
    return this.pitch()?.[tab] ?? '';
  }

  wordCount(tab: PitchTab): number {
    const t = this.textOf(tab).trim();
    return t ? t.split(/\s+/).length : 0;
  }

  async copy(tab: PitchTab) {
    const text = this.textOf(tab);
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // Repli si l'API Clipboard est indisponible (http, navigateur ancien)
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    this.copied.set(tab);
    setTimeout(() => { if (this.copied() === tab) this.copied.set(''); }, 2000);
  }

  reset() {
    this.pitch.set(null);
    this.error.set('');
  }

  /** Message lisible à partir de la réponse d'erreur (ProblemDetail : champ "detail"). */
  private messageFor(err: HttpErrorResponse): string {
    const detail = err.error?.detail ?? err.error?.message;
    if (err.status === 0) {
      return 'Impossible de joindre le serveur. Vérifiez que les services sont démarrés.';
    }
    if (err.status === 429) {
      const retry = err.headers?.get('Retry-After');
      return (detail || 'Trop de requêtes.') + (retry ? ` Réessayez dans ${retry} s.` : '');
    }
    if (typeof detail === 'string' && detail.trim()) return detail;
    if (err.status === 401 || err.status === 403) return 'Session expirée. Reconnectez-vous.';
    if (err.status === 400) return 'Requête invalide.';
    if (err.status === 503) return 'Le service d\'IA est temporairement indisponible.';
    return 'La génération du pitch a échoué. Veuillez réessayer.';
  }
}