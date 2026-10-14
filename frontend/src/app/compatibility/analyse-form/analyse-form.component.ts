import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CompatibilityService, apiErrorMessage } from '../compatibility.service';

@Component({
  selector: 'app-analyse-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './analyse-form.component.html',
  styleUrls: ['./analyse-form.component.scss']
})
export class AnalyseFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CompatibilityService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Mêmes limites que le backend (5 Mo, 150 / 50-8000 caractères). */
  private readonly maxFileBytes = 5 * 1024 * 1024;

  readonly form = this.fb.nonNullable.group({
    jobTitle: ['', [Validators.required, Validators.maxLength(150)]],
    jobDescription: ['', [Validators.required, Validators.minLength(50), Validators.maxLength(8000)]]
  });

  readonly file = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly dragging = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly steps = [
    'Lecture du CV (PDF)',
    'Masquage des données personnelles',
    'Analyse par l\'IA locale (Ollama)',
    'Validation du résultat'
  ];
  readonly stepIndex = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopSteps());
  }

  // ── Fichier ────────────────────────────────────────────────────────────────

  onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.setFile(input.files?.[0] ?? null);
    input.value = ''; // permet de re-sélectionner le même fichier
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.setFile(event.dataTransfer?.files?.[0] ?? null);
  }

  removeFile(event: Event): void {
    event.stopPropagation();
    this.file.set(null);
    this.fileError.set(null);
  }

  private setFile(f: File | null): void {
    if (!f) return;
    if (!f.name.toLowerCase().endsWith('.pdf')) {
      this.file.set(null);
      this.fileError.set('Seuls les fichiers PDF sont acceptés.');
    } else if (f.size === 0) {
      this.file.set(null);
      this.fileError.set('Le fichier est vide.');
    } else if (f.size > this.maxFileBytes) {
      this.file.set(null);
      this.fileError.set('Le fichier dépasse 5 Mo.');
    } else {
      this.file.set(f);
      this.fileError.set(null);
    }
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.max(1, Math.round(bytes / 1024))} Ko`
      : `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  // ── Formulaire ─────────────────────────────────────────────────────────────

  invalid(name: 'jobTitle' | 'jobDescription'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  get descriptionLength(): number {
    return this.form.controls.jobDescription.value.length;
  }

  submit(): void {
    if (this.loading()) return;
    this.form.markAllAsTouched();
    const cv = this.file();
    if (!cv) {
      this.fileError.set('Le CV (PDF) est obligatoire.');
    }
    if (this.form.invalid || !cv) return;

    const { jobTitle, jobDescription } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.startSteps();

    this.service.create(cv, jobTitle.trim(), jobDescription.trim()).subscribe({
      next: analysis => {
        this.stopSteps();
        this.router.navigate(['/frontoffice/compatibility', analysis.id]);
      },
      error: err => {
        this.stopSteps();
        this.loading.set(false);
        this.error.set(apiErrorMessage(err));
      }
    });
  }

  // ── Progression animée (l'analyse IA peut durer plusieurs secondes) ────────

  private startSteps(): void {
    this.stepIndex.set(0);
    this.stopSteps();
    this.timer = setInterval(() => {
      this.stepIndex.update(i => Math.min(i + 1, this.steps.length - 1));
    }, 4000); // un modèle local est plus lent
  }

  private stopSteps(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }
}