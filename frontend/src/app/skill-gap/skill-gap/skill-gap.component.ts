import { Component, HostListener, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SkillGapService } from '../services/skill-gap.service';
import { CvUpload, Priority, SkillGapAnalysis } from '../models/skill-gap.model';

const MAX_SIZE = 5 * 1024 * 1024;
const ALLOWED_EXT = ['pdf', 'docx', 'txt'];
export const JOB_MIN = 50;    // mêmes bornes que AnalysisCreateRequest (backend)
export const JOB_MAX = 6000;

/** Élément en attente de confirmation de suppression. */
interface PendingDelete {
  kind: 'cv' | 'analysis';
  id: number;
  name: string;
  linked: number;   // analyses supprimées avec le CV
}

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
  dragOver = signal(false);
  error = signal('');

  jobLength = computed(() => this.jobDescription().trim().length);
  jobProgress = computed(() => Math.min(100, Math.round(this.jobLength() / JOB_MIN * 100)));
  /** Nombre de compétences à améliorer par priorité, pour le résumé du résultat. */
  priorityCounts = computed(() => {
    const counts: Record<Priority, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    for (const m of this.result()?.result.missingSkills ?? []) counts[m.priority] = (counts[m.priority] ?? 0) + 1;
    return counts;
  });
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
    if (file) this.upload(file);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    if (!this.uploading()) this.dragOver.set(true);
  }

  onDragLeave() {
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file && !this.uploading()) this.upload(file);
  }

  private upload(file: File) {
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

  // ── Suppression (fenêtre de confirmation) ───────────────────────────────

  pendingDelete = signal<PendingDelete | null>(null);
  deleting = signal(false);

  deleteCv(cv: CvUpload, event: Event) {
    event.stopPropagation();
    const linked = this.history().filter(a => a.cvId === cv.id).length;
    this.pendingDelete.set({ kind: 'cv', id: cv.id, name: cv.fileName, linked });
  }

  deleteAnalysis(a: SkillGapAnalysis, event: Event) {
    event.stopPropagation();
    this.pendingDelete.set({ kind: 'analysis', id: a.id, name: a.jobTitle || 'Offre sans titre', linked: 0 });
  }

  cancelDelete() {
    if (!this.deleting()) this.pendingDelete.set(null);
  }

  confirmDelete() {
    const p = this.pendingDelete();
    if (!p || this.deleting()) return;
    this.deleting.set(true);
    const call = p.kind === 'cv' ? this.api.deleteCv(p.id) : this.api.deleteAnalysis(p.id);
    call.subscribe({
      next: () => {
        if (p.kind === 'cv') {
          this.cvs.update(list => list.filter(c => c.id !== p.id));
          this.history.update(list => list.filter(a => a.cvId !== p.id));
          if (this.selectedCvId() === p.id) this.selectedCvId.set(this.cvs()[0]?.id ?? null);
          if (this.result()?.cvId === p.id) this.result.set(null);
        } else {
          this.history.update(list => list.filter(x => x.id !== p.id));
          if (this.result()?.id === p.id) this.result.set(null);
        }
        this.deleting.set(false);
        this.pendingDelete.set(null);
      },
      error: e => {
        this.deleting.set(false);
        this.pendingDelete.set(null);
        this.fail(e, 'Suppression impossible.');
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.cancelDelete();
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