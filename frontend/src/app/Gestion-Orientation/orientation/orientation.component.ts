import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrientationService } from '../services/orientation.service';
import { Advice } from '../models/orientation.model';

/**
 * Page "Conseiller d'orientation" (frontoffice) — POST /api/orientation/advice (competence-service + Gemini).
 */
@Component({
  selector: 'app-orientation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './orientation.component.html',
  styleUrls: ['./orientation.component.scss']
})
export class OrientationComponent {
  readonly MIN = 20;
  readonly MAX = 1500;

  prompt = '';
  advice = signal<Advice | null>(null);
  loading = signal(false);
  error = signal('');

  readonly modele = `Rôle : Tu es un conseiller d'orientation professionnelle.

Contexte : (décris ton profil : formation, compétences, niveau)

Question : (ce que tu veux savoir)

Format de réponse : (liste, tableau, nombre de points, ton…)`;

  readonly exemple = `Rôle : Tu es un conseiller d'orientation professionnelle.

Contexte : Je suis étudiant en 4e année d'ingénierie informatique, je connais Java, Spring Boot, Angular et MySQL.

Question : Je veux devenir data scientist, quelles formations ou certifications dois-je ajouter ?

Format de réponse : 5 points maximum, avec une raison courte pour chacun.`;

  constructor(private api: OrientationService) {}

  envoyer() {
    if (this.loading()) return;
    if (this.prompt.trim().length < this.MIN) {
      this.error.set('Ton prompt est trop court, utilise le modèle pour le compléter.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.advice.set(null);

    this.api.advise(this.prompt).subscribe({
      next: res => { this.advice.set(res.data); this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(this.msg(err)); }
    });
  }

  /** Découpe le texte sur **gras** pour l'afficher sans innerHTML (pas de risque XSS). */
  segments(text: string): { t: string; bold: boolean }[] {
    return text.split(/\*\*/).map((t, i) => ({ t, bold: i % 2 === 1 })).filter(s => s.t);
  }

  private msg(err: any): string {
    if (err?.status === 0) return 'Serveur injoignable (vérifiez que l\'API Gateway est démarrée).';
    return err?.error?.message || 'Service indisponible, réessaie.';
  }
}
