import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompetenceService } from '../services/competence.service';
import {
  CATEGORIES, Competence, CompetenceRequest, NIVEAUX
} from '../models/competence.model';

/**
 * Page "Mes compétences" (frontoffice) — CRUD complet via /api/competences (competence-service).
 */
@Component({
  selector: 'app-competences',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './competences.component.html',
  styleUrls: ['./competences.component.scss']
})
export class CompetencesComponent implements OnInit {
  readonly niveaux = NIVEAUX;
  readonly categories = CATEGORIES;

  competences = signal<Competence[]>([]);
  filter = signal<string>('ALL');
  filtered = computed(() =>
    this.filter() === 'ALL' ? this.competences() : this.competences().filter(c => c.categorie === this.filter()));

  loading = signal(true);
  saving = signal(false);
  success = signal('');
  pageError = signal('');
  formError = signal('');

  showForm = false;
  editingId: number | null = null;
  toDelete: Competence | null = null;

  form: CompetenceRequest = this.emptyForm();

  constructor(private api: CompetenceService) {}

  ngOnInit() { this.load(); }

  private emptyForm(): CompetenceRequest {
    return { nom: '', niveau: 'INTERMEDIAIRE', categorie: 'TECHNIQUE' };
  }

  load() {
    this.loading.set(true);
    this.api.getCompetences().subscribe({
      next: res => { this.competences.set(res.data ?? []); this.loading.set(false); },
      error: err => { this.loading.set(false); this.pageError.set(this.msg(err, 'Impossible de charger les compétences.')); }
    });
  }

  niveauLabel(v: string) { return NIVEAUX.find(n => n.value === v)?.label ?? v; }
  niveauRank(v: string) { return NIVEAUX.find(n => n.value === v)?.rank ?? 0; }
  categorieLabel(v: string) { return CATEGORIES.find(c => c.value === v)?.label ?? v; }

  openCreate() {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formError.set('');
    this.showForm = true;
  }

  openEdit(c: Competence) {
    this.editingId = c.id;
    this.form = { nom: c.nom, niveau: c.niveau, categorie: c.categorie };
    this.formError.set('');
    this.showForm = true;
  }

  closeForm() { this.showForm = false; }

  save() {
    if (!this.form.nom?.trim()) { this.formError.set('Le nom de la compétence est obligatoire.'); return; }
    const payload: CompetenceRequest = { ...this.form, nom: this.form.nom.trim() };

    this.saving.set(true);
    const call = this.editingId == null
      ? this.api.createCompetence(payload)
      : this.api.updateCompetence(this.editingId, payload);

    call.subscribe({
      next: res => {
        this.saving.set(false);
        this.showForm = false;
        this.toast(res.message || 'Enregistré');
        this.load();
      },
      error: err => { this.saving.set(false); this.formError.set(this.msg(err, 'Échec de l\'enregistrement.')); }
    });
  }

  askDelete(c: Competence) { this.toDelete = c; }

  confirmDelete() {
    if (!this.toDelete) return;
    this.api.deleteCompetence(this.toDelete.id).subscribe({
      next: res => { this.toDelete = null; this.toast(res.message || 'Compétence supprimée'); this.load(); },
      error: err => { this.toDelete = null; this.pageError.set(this.msg(err, 'Suppression impossible.')); }
    });
  }

  private toast(message: string) {
    this.pageError.set('');
    this.success.set(message);
    setTimeout(() => this.success.set(''), 3000);
  }

  private msg(err: any, fallback: string): string {
    if (err?.status === 0) return 'Serveur injoignable (vérifiez que l\'API Gateway est démarrée).';
    return err?.error?.message || fallback;
  }
}
