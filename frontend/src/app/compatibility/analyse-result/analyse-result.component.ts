import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AnalysisResponse, formatDuration, scoreLabel, scoreLevel } from '../compatibility.models';
import { CompatibilityService, apiErrorMessage } from '../compatibility.service';

@Component({
  selector: 'app-analyse-result',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './analyse-result.component.html',
  styleUrls: ['./analyse-result.component.scss']
})
export class AnalyseResultComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(CompatibilityService);

  readonly analysis = signal<AnalysisResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly confirmDelete = signal(false);
  readonly deleting = signal(false);
  readonly showOffer = signal(false);

  /** Jauge circulaire SVG (rayon 54) */
  readonly circumference = 2 * Math.PI * 54;
  readonly level = computed(() => scoreLevel(this.analysis()?.score ?? 0));
  readonly label = computed(() => scoreLabel(this.analysis()?.score ?? 0));
  readonly dashOffset = computed(() => this.circumference * (1 - (this.analysis()?.score ?? 0) / 100));
  readonly metrics = computed(() => this.analysis()?.metrics ?? null);
  readonly duration = computed(() => formatDuration(this.metrics()?.latencyMs ?? 0));
  readonly tokens = computed(() => {
    const m = this.metrics();
    return m ? m.promptTokens + m.completionTokens : 0;
  });

  private id = 0;

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(this.id) || this.id <= 0) {
      this.loading.set(false);
      this.error.set('Identifiant d\'analyse invalide.');
      return;
    }
    this.service.get(this.id).subscribe({
      next: a => {
        this.analysis.set(a);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  // ── UPDATE : renommer ──────────────────────────────────────────────────────

  startEdit(): void {
    this.actionError.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.actionError.set(null);
  }

  saveTitle(value: string): void {
    const title = value.trim();
    if (!title || title.length > 150) {
      this.actionError.set('L\'intitulé est obligatoire (150 caractères maximum).');
      return;
    }
    if (title === this.analysis()?.jobTitle) {
      this.cancelEdit();
      return;
    }
    this.saving.set(true);
    this.actionError.set(null);
    this.service.rename(this.id, title).subscribe({
      next: a => {
        this.analysis.set(a);
        this.saving.set(false);
        this.editing.set(false);
      },
      error: err => {
        this.saving.set(false);
        this.actionError.set(apiErrorMessage(err));
      }
    });
  }

  // ── DELETE ─────────────────────────────────────────────────────────────────

  remove(): void {
    this.deleting.set(true);
    this.actionError.set(null);
    this.service.delete(this.id).subscribe({
      next: () => this.router.navigate(['/frontoffice/compatibility']),
      error: err => {
        this.deleting.set(false);
        this.confirmDelete.set(false);
        this.actionError.set(apiErrorMessage(err));
      }
    });
  }
}