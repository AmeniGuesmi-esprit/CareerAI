import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ExperienceService } from '../services/experience.service';
import { Experience, TYPES_CONTRAT, TypeContrat, labelContrat } from '../models/experience.model';

/**
 * Page "Mes expériences" (frontoffice USER) : liste, recherche, filtre par type de contrat,
 * suppression avec confirmation. L'ajout / la modification passent par ExperienceFormComponent.
 */
@Component({
  selector: 'app-experience-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './experience-list.component.html',
  styleUrls: ['./experience-list.component.scss']
})
export class ExperienceListComponent implements OnInit {
  readonly types = TYPES_CONTRAT;
  readonly label = labelContrat;

  experiences = signal<Experience[]>([]);
  loading = signal(true);
  error = signal('');
  success = signal('');

  search = signal('');
  typeFilter = signal<TypeContrat | ''>('');

  toDelete = signal<Experience | null>(null);
  deleting = signal(false);

  filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const type = this.typeFilter();
    return this.experiences().filter(e =>
      (!type || e.typeContrat === type) &&
      (!q || [e.poste, e.entreprise, e.lieu ?? ''].some(v => v.toLowerCase().includes(q)))
    );
  });

  enCoursCount = computed(() => this.experiences().filter(e => e.enCours).length);

  constructor(private service: ExperienceService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.service.getAll().subscribe({
      next: res => {
        this.experiences.set(res.data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos expériences. Vérifiez que les services sont démarrés.');
        this.loading.set(false);
      }
    });
  }

  /** Durée lisible, ex. "1 an 3 mois". */
  duree(e: Experience): string {
    const start = new Date(e.dateDebut);
    const end = e.enCours || !e.dateFin ? new Date() : new Date(e.dateFin);
    let months = (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth());
    if (end.getDate() >= start.getDate()) months += 1; // mois entamé compté
    months = Math.max(months, 1);
    const y = Math.floor(months / 12);
    const m = months % 12;
    const parts: string[] = [];
    if (y) parts.push(`${y} an${y > 1 ? 's' : ''}`);
    if (m) parts.push(`${m} mois`);
    return parts.join(' ');
  }

  askDelete(e: Experience) {
    this.toDelete.set(e);
  }

  cancelDelete() {
    if (!this.deleting()) this.toDelete.set(null);
  }

  confirmDelete() {
    const e = this.toDelete();
    if (!e) return;
    this.deleting.set(true);
    this.service.delete(e.id).subscribe({
      next: () => {
        this.experiences.update(list => list.filter(x => x.id !== e.id));
        this.deleting.set(false);
        this.toDelete.set(null);
        this.flash('Expérience supprimée.');
      },
      error: () => {
        this.deleting.set(false);
        this.toDelete.set(null);
        this.error.set('La suppression a échoué.');
        setTimeout(() => this.error.set(''), 3500);
      }
    });
  }

  private flash(msg: string) {
    this.success.set(msg);
    setTimeout(() => this.success.set(''), 3000);
  }
}
