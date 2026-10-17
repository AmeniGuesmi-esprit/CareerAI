import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { InterviewService } from '../interview.service';
import { QuestionView, RagHit, RagView, SessionDetail } from '../models/interview.model';
import { RagTracePanelComponent } from '../rag-panel/rag-trace-panel.component';

const POLL_INTERVAL_MS = 3000;

@Component({
  selector: 'app-interview-session-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, RagTracePanelComponent],
  templateUrl: './interview-session-detail.component.html',
  styleUrls: ['./interview-session-detail.component.scss']
})
export class InterviewSessionDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(InterviewService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  session: SessionDetail | null = null;
  rag: RagView | null = null;
  showRag = false;
  drafts: Record<number, string> = {};
  savingQuestionId: number | null = null;
  editingTitle = false;
  titleDraft = '';
  error = '';

  private sessionId = 0;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private ragLoaded = false;

  ngOnInit() {
    this.sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  load() {
    this.api.getSession(this.sessionId).subscribe({
      next: session => {
        this.session = session;
        this.error = '';
        for (const q of session.questions) {
          if (!(q.id in this.drafts)) this.drafts[q.id] = q.answer?.content ?? '';
        }
        if (session.status === 'READY' && !this.ragLoaded) this.loadRag();
        this.schedulePolling();
      },
      error: err => (this.error = err.error?.message ?? 'Session introuvable.')
    });
  }

  loadRag() {
    this.api.getRag(this.sessionId).subscribe({
      next: rag => {
        this.rag = rag;
        this.ragLoaded = true;
      },
      error: () => (this.ragLoaded = false)
    });
  }

  toggleRag() {
    this.showRag = !this.showRag;
    if (this.showRag && !this.rag) this.loadRag();
  }

  sourcesFor(q: QuestionView): RagHit[] {
    const used = this.rag?.questions.find(x => x.questionId === q.id)?.usedCitationIds ?? [];
    return (this.rag?.retrieved ?? []).filter(h => used.includes(h.citationId));
  }

  saveAnswer(q: QuestionView) {
    const content = (this.drafts[q.id] ?? '').trim();
    if (!content || !this.session) return;
    this.savingQuestionId = q.id;
    this.api.saveAnswer(this.session.id, q.id, content).subscribe({
      next: () => {
        this.savingQuestionId = null;
        this.load();
      },
      error: err => {
        this.error = err.error?.message ?? 'Enregistrement impossible.';
        this.savingQuestionId = null;
      }
    });
  }

  deleteAnswer(q: QuestionView) {
    if (!this.session || !confirm('Supprimer cette réponse et son feedback ?')) return;
    this.api.deleteAnswer(this.session.id, q.id).subscribe({
      next: () => {
        this.drafts[q.id] = '';
        this.load();
      },
      error: err => (this.error = err.error?.message ?? 'Suppression impossible.')
    });
  }

  retry() {
    if (!this.session) return;
    this.ragLoaded = false;
    this.api.retrySession(this.session.id).subscribe({
      next: () => this.load(),
      error: err => (this.error = err.error?.message ?? 'Relance impossible.')
    });
  }

  startEditTitle() {
    if (!this.session) return;
    this.titleDraft = this.session.jobTitle;
    this.editingTitle = true;
  }

  saveTitle() {
    if (!this.session || this.titleDraft.trim().length < 2) return;
    this.api.renameSession(this.session.id, this.titleDraft).subscribe({
      next: () => {
        this.editingTitle = false;
        this.load();
      },
      error: err => (this.error = err.error?.message ?? 'Modification impossible.')
    });
  }

  deleteSession() {
    if (!this.session || !confirm('Supprimer cette session, ses questions et ses réponses ?')) return;
    this.api.deleteSession(this.session.id).subscribe({
      next: () => this.router.navigate(['/frontoffice/interview']),
      error: err => (this.error = err.error?.message ?? 'Suppression impossible.')
    });
  }

  isPending(q: QuestionView): boolean {
    return q.answer?.feedback?.status === 'PENDING';
  }

  private schedulePolling() {
    this.stopPolling();
    const busy = this.session?.status === 'GENERATING'
      || (this.session?.questions ?? []).some(q => this.isPending(q));
    if (busy) {
      this.pollTimer = setTimeout(() => this.load(), POLL_INTERVAL_MS);
    }
  }

  private stopPolling() {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
