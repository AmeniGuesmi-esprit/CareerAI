import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { LettreService } from '../services/lettre.service';
import { LettreRequest } from '../models/lettre.model';

/**
 * Formulaire de lettre de motivation : nom de l'entreprise, (poste), CV, lettre.
 * Mode création : /frontoffice/lettres/new — mode modification : /frontoffice/lettres/:id/edit
 */
@Component({
  selector: 'app-lettre-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './lettre-form.component.html',
  styleUrl: './lettre-form.component.scss'
})
export class LettreFormComponent implements OnInit {
  readonly MAX_TEXT = 20000;
  readonly MAX_SHORT = 150;

  id: number | null = null;
  loading = false;
  saving = false;
  submitted = false;
  error = '';

  form: LettreRequest = { nomEntreprise: '', poste: '', cv: '', contenu: '' };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private lettreService: LettreService,
    private cdr: ChangeDetectorRef
  ) {}

  get isEdit(): boolean {
    return this.id !== null;
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id = Number(idParam);
      this.loadLettre(this.id);
    }
  }

  private loadLettre(id: number): void {
    this.loading = true;
    this.lettreService.getById(id).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.success && res.data) {
          this.form = {
            nomEntreprise: res.data.nomEntreprise,
            poste: res.data.poste ?? '',
            cv: res.data.cv,
            contenu: res.data.contenu
          };
        } else {
          this.error = res.message || 'Lettre introuvable.';
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.error = 'Impossible de charger la lettre.';
        this.cdr.detectChanges();
      }
    });
  }

  // ── Validation (mêmes règles que le backend) ──
  get nomError(): string {
    const v = this.form.nomEntreprise.trim();
    if (!v) return 'Le nom de l\'entreprise est obligatoire';
    if (v.length > this.MAX_SHORT) return `Maximum ${this.MAX_SHORT} caractères`;
    return '';
  }

  get posteError(): string {
    return (this.form.poste ?? '').trim().length > this.MAX_SHORT ? `Maximum ${this.MAX_SHORT} caractères` : '';
  }

  get cvError(): string {
    const v = this.form.cv.trim();
    if (!v) return 'Le CV est obligatoire';
    if (v.length > this.MAX_TEXT) return `Maximum ${this.MAX_TEXT} caractères`;
    return '';
  }

  get contenuError(): string {
    const v = this.form.contenu.trim();
    if (!v) return 'La lettre de motivation est obligatoire';
    if (v.length > this.MAX_TEXT) return `Maximum ${this.MAX_TEXT} caractères`;
    return '';
  }

  get isValid(): boolean {
    return !this.nomError && !this.posteError && !this.cvError && !this.contenuError;
  }

  submit(): void {
    this.submitted = true;
    this.error = '';
    if (!this.isValid) return;

    const request: LettreRequest = {
      nomEntreprise: this.form.nomEntreprise.trim(),
      poste: (this.form.poste ?? '').trim(),
      cv: this.form.cv.trim(),
      contenu: this.form.contenu.trim()
    };

    this.saving = true;
    const call$ = this.isEdit
      ? this.lettreService.update(this.id as number, request)
      : this.lettreService.create(request);

    call$.subscribe({
      next: (res) => {
        this.saving = false;
        if (res.success && res.data) {
          this.router.navigate(['/frontoffice/lettres', res.data.id]);
        } else {
          this.error = res.message || 'Échec de l\'enregistrement.';
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        this.saving = false;
        this.error = err?.error?.message || 'Erreur lors de l\'enregistrement.';
        this.cdr.detectChanges();
      }
    });
  }
}
