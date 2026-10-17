import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ExperienceService } from '../services/experience.service';
import { ExperienceRequest, TYPES_CONTRAT } from '../models/experience.model';

/** Validateur de groupe : date de fin obligatoire (et >= début) sauf si "en cours". */
function datesValidator(group: AbstractControl): ValidationErrors | null {
  const enCours = group.get('enCours')?.value;
  const debut = group.get('dateDebut')?.value;
  const fin = group.get('dateFin')?.value;
  if (enCours) return null;
  if (!fin) return { finRequise: true };
  if (debut && fin < debut) return { finAvantDebut: true }; // 'yyyy-MM-dd' se compare comme du texte
  return null;
}

/**
 * Formulaire d'ajout (/frontoffice/experiences/new) et de modification
 * (/frontoffice/experiences/:id/edit) d'une expérience.
 */
@Component({
  selector: 'app-experience-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './experience-form.component.html',
  styleUrls: ['./experience-form.component.scss']
})
export class ExperienceFormComponent implements OnInit {
  readonly types = TYPES_CONTRAT;

  editId: number | null = null;
  loading = signal(false);
  saving = signal(false);
  error = signal('');
  submitted = signal(false);

  form = this.fb.group({
    poste: ['', [Validators.required, Validators.maxLength(150)]],
    entreprise: ['', [Validators.required, Validators.maxLength(150)]],
    lieu: ['', [Validators.maxLength(150)]],
    typeContrat: ['CDI', [Validators.required]],
    dateDebut: ['', [Validators.required]],
    dateFin: [''],
    enCours: [false],
    description: ['', [Validators.maxLength(2000)]]
  }, { validators: datesValidator });

  constructor(
    private fb: FormBuilder,
    private service: ExperienceService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = +id;
      this.loading.set(true);
      this.service.getById(this.editId).subscribe({
        next: res => {
          const e = res.data;
          this.form.patchValue({
            poste: e.poste,
            entreprise: e.entreprise,
            lieu: e.lieu ?? '',
            typeContrat: e.typeContrat,
            dateDebut: e.dateDebut,
            dateFin: e.dateFin ?? '',
            enCours: e.enCours,
            description: e.description ?? ''
          });
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set('Expérience introuvable.');
        }
      });
    }
  }

  get isEdit(): boolean { return this.editId !== null; }
  get enCours(): boolean { return !!this.form.get('enCours')?.value; }
  get descLength(): number { return (this.form.get('description')?.value ?? '').length; }

  /** Affiche l'erreur d'un champ après soumission ou une fois touché. */
  invalid(name: string): boolean {
    const c = this.form.get(name);
    return !!c && c.invalid && (c.touched || this.submitted());
  }

  onEnCoursChange() {
    if (this.enCours) this.form.patchValue({ dateFin: '' });
  }

  submit() {
    this.submitted.set(true);
    this.error.set('');
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    const v = this.form.getRawValue();
    const body: ExperienceRequest = {
      poste: (v.poste ?? '').trim(),
      entreprise: (v.entreprise ?? '').trim(),
      lieu: v.lieu?.trim() || null,
      typeContrat: v.typeContrat as ExperienceRequest['typeContrat'],
      dateDebut: v.dateDebut ?? '',
      dateFin: v.enCours ? null : (v.dateFin || null),
      enCours: !!v.enCours,
      description: v.description?.trim() || null
    };

    this.saving.set(true);
    const call = this.isEdit ? this.service.update(this.editId!, body) : this.service.create(body);
    call.subscribe({
      next: () => this.router.navigate(['/frontoffice/experiences']),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(err.error?.message || 'Échec de l\'enregistrement. Réessayez.');
      }
    });
  }
}
