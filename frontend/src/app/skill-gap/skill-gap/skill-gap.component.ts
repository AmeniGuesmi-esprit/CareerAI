import { Component, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SkillGapService } from '../services/skill-gap.service';
import { CvUpload, Priority, SkillGapAnalysis } from '../models/skill-gap.model';

const MAX_SIZE = 5 * 1024 * 1024;
const ALLOWED_EXT = ['pdf', 'docx', 'txt'];
export const JOB_MIN = 50;    // mêmes bornes que AnalysisCreateRequest (backend)
export const JOB_MAX = 6000;

const PRIORITY_LABEL: Record<Priority, string> = {
  HIGH: 'Prioritaire',
  MEDIUM: 'Important',
  LOW: 'Un plus'
};

/**
 * Page "Analyse CV / offre" (frontoffice) : import d'un CV + description d'une offre d'emploi,
 * le LLM (skill-gap-service) indique les compétences à améliorer. Zoneless : état en signals.
 */
@Component({
  selector: 'app-skill-gap',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './skill-gap.component.html',
  styleUrls: ['./skill-gap.component.scss']
})
export class SkillGapComponent implements OnInit {
  readonly JOB_MIN = JOB_MIN;
  readonly JOB_MAX = JOB_MAX;

  cvs = signal<CvUpload[]>([]);
  history = signal<SkillGapAnalysis[]>([]);

  selectedCvId = signal<number | null>(null);
  jobTitle = signal('');
  jobDescription = signal('');
  result = signal<SkillGapAnalysis | null>(null);

  uploading = signal(false);
  analyzing = signal(false);
  error = signal('');

  jobLength = computed(() => this.jobDescription().trim().length);
  canAnalyze = computed(() =>
    !!this.selectedCvId() && !this.analyzing() &&
    this.jobLength() >= JOB_MIN && this.jobLength() <= JOB_MAX);

  constructor(private api: SkillGapService) {}

  ngOnInit() {
    this.api.getMyCvs().subscribe({
      next: res => {
        this.cvs.set(res.data ?? []);
        if (!this.selectedCvId() && res.data?.length) this.selectedCvId.set(res.data[0].id);
      },
      error: e => this.fail(e, 'Impossible de charger vos CV.')
    });
    this.api.getMyAnalyses().subscribe({
      next: res => this.history.set(res.data ?? []),
      error: e => this.fail(e, "Impossible de charger l'historique.")
    });
  }

  // ── Upload ──────────────────────────────────────────────────────────────

  onFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // permet de re-sélectionner le même fichier
    if (!file) return;

    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!ALLOWED_EXT.includes(ext)) {
      this.error.set('Format non supporté (PDF, DOCX ou TXT).');
      return;
    }
    if (file.size > MAX_SIZE) {
      this.error.set('Fichier trop volumineux (5 Mo maximum).');
      return;
    }

    this.error.set('');
    this.uploading.set(true);
    this.api.uploadCv(file).subscribe({
      next: res => {
        this.uploading.set(false);
        if (res.data) {
          this.cvs.update(list => [res.data, ...list]);
          this.selectedCvId.set(res.data.id);
        }
      },
      error: e => {
        this.uploading.set(false);
        this.fail(e, "Échec de l'import du CV.");
      }
    });
  }

  deleteCv(cv: CvUpload, event: Event) {
    event.stopPropagation();
    if (!confirm(`Supprimer « ${cv.fileName} » et ses analyses ?`)) return;

    this.api.deleteCv(cv.id).subscribe({
      next: () => {
        this.cvs.update(list => list.filter(c => c.id !== cv.id));
        this.history.update(list => list.filter(a => a.cvId !== cv.id));
        if (this.selectedCvId() === cv.id) this.selectedCvId.set(this.cvs()[0]?.id ?? null);
        if (this.result()?.cvId === cv.id) this.result.set(null);
      },
      error: e => this.fail(e, 'Suppression impossible.')
    });
  }

  // ── Analyse ─────────────────────────────────────────────────────────────

  onJobTitle(event: Event) {
    this.jobTitle.set((event.target as HTMLInputElement).value);
  }

  onJobDescription(event: Event) {
    this.jobDescription.set((event.target as HTMLTextAreaElement).value);
  }

  analyze() {
    const cvId = this.selectedCvId();
    if (!cvId || !this.canAnalyze()) return;

    this.error.set('');
    this.result.set(null);
    this.analyzing.set(true);
    this.api.analyze(cvId, this.jobTitle().trim(), this.jobDescription().trim()).subscribe({
      next: res => {
        this.analyzing.set(false);
        if (res.data) {
          this.result.set(res.data);
          this.history.update(list => [res.data, ...list]);
        }
      },
      error: e => {
        this.analyzing.set(false);
        this.fail(e, "L'analyse a échoué.");
      }
    });
  }

  view(a: SkillGapAnalysis) {
    this.error.set('');
    this.result.set(a);
  }

  deleteAnalysis(a: SkillGapAnalysis, event: Event) {
    event.stopPropagation();
    this.api.deleteAnalysis(a.id).subscribe({
      next: () => {
        this.history.update(list => list.filter(x => x.id !== a.id));
        if (this.result()?.id === a.id) this.result.set(null);
      },
      error: e => this.fail(e, 'Suppression impossible.')
    });
  }

  // ── Affichage ───────────────────────────────────────────────────────────

  priorityLabel(p: Priority): string {
    return PRIORITY_LABEL[p] ?? p;
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} Ko` : `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  }

  private fail(err: any, fallback: string) {
    this.error.set(err?.error?.message || fallback);
  }
}