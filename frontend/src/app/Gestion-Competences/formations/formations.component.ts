import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompetenceService } from '../services/competence.service';
import { Formation, FormationRequest } from '../models/competence.model';

/**
 * Page "Mes formations" (frontoffice) — CRUD complet via /api/formations (competence-service).
 */
@Component({
  selector: 'app-formations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './formations.component.html',
  styleUrls: ['./formations.component.scss']
})
export class FormationsComponent implements OnInit {
  formations = signal<Formation[]>([]);
  loading = signal(true);
  saving = signal(false);
  success = signal('');
  pageError = signal('');
  formError = signal('');

  showForm = false;
  editingId: number | null = null;
  toDelete: Formation | null = null;

  form: FormationRequest = this.emptyForm();

  constructor(private api: CompetenceService) {}

  ngOnInit() { this.load(); }

  private emptyForm(): FormationRequest {
    return { diplome: '', etablissement: '', domaine: '', dateDebut: '', dateFin: '', enCours: false, description: '' };
  }

  load() {
    this.loading.set(true);
    this.api.getFormations().subscribe({
      next: res => { this.formations.set(res.data ?? []); this.loading.set(false); },
      error: err => { this.loading.set(false); this.pageError.set(this.msg(err, 'Impossible de charger les formations.')); }
    });
  }

  openCreate() {
    this.editingId = null;
    this.form = this.emptyForm();
    this.formError.set('');
    this.showForm = true;
  }

  openEdit(f: Formation) {
    this.editingId = f.id;
    this.form = {
      diplome: f.diplome, etablissement: f.etablissement, domaine: f.domaine ?? '',
      dateDebut: f.dateDebut, dateFin: f.dateFin ?? '', enCours: f.enCours, description: f.description ?? ''
    };
    this.formError.set('');
    this.showForm = true;
  }

  closeForm() { this.showForm = false; }

  onEnCoursChange() { if (this.form.enCours) this.form.dateFin = ''; }

  save() {
    const f = this.form;
    if (!f.diplome?.trim() || !f.etablissement?.trim() || !f.dateDebut) {
      this.formError.set('Diplôme, établissement et date de début sont obligatoires.'); return;
    }
    if (!f.enCours && !f.dateFin) { this.formError.set('La date de fin est obligatoire si la formation n\'est pas en cours.'); return; }
    if (!f.enCours && f.dateFin && f.dateFin < f.dateDebut) {
      this.formError.set('La date de fin doit être postérieure à la date de début.'); return;
    }

    const payload: FormationRequest = {
      ...f,
      domaine: f.domaine?.trim() || null,
      dateFin: f.enCours ? null : f.dateFin,
      description: f.description?.trim() || null
    };

    this.saving.set(true);
    const call = this.editingId == null
      ? this.api.createFormation(payload)
      : this.api.updateFormation(this.editingId, payload);

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

  askDelete(f: Formation) { this.toDelete = f; }

  confirmDelete() {
    if (!this.toDelete) return;
    const id = this.toDelete.id;
    this.api.deleteFormation(id).subscribe({
      next: res => { this.toDelete = null; this.toast(res.message || 'Formation supprimée'); this.load(); },
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
