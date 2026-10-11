import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RagHit, RagView } from '../models/interview.model';

const TRACK_LABELS: Record<string, string> = {
  'backend-java-spring': 'Backend Java / Spring',
  'frontend-angular': 'Frontend Angular',
  generic: 'Générique'
};

const DOC_LABELS: Record<RagHit['docType'], string> = {
  question_bank: 'Question type',
  guide: 'Méthode',
  rubric: 'Grille'
};

@Component({
  selector: 'app-rag-trace-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rag-trace-panel.component.html',
  styleUrls: ['./rag-trace-panel.component.scss']
})
export class RagTracePanelComponent {
  @Input({ required: true }) rag!: RagView;

  trackLabel(track: string): string {
    return TRACK_LABELS[track] ?? track;
  }

  docLabel(type: RagHit['docType']): string {
    return DOC_LABELS[type] ?? type;
  }

  get maxScore(): number {
    return Math.max(...this.rag.retrieved.map(h => h.score), 0.0001);
  }

  barWidth(hit: RagHit): number {
    return Math.round((hit.score / this.maxScore) * 100);
  }

  usedBy(citationId: string): string {
    return this.rag.questions
      .filter(q => q.usedCitationIds.includes(citationId))
      .map(q => `Q${q.position}`)
      .join(', ');
  }

  get usedCount(): number {
    return this.rag.retrieved.filter(h => this.usedBy(h.citationId)).length;
  }

  get shortHash(): string {
    return this.rag.manifestHash ? this.rag.manifestHash.slice(0, 10) : '—';
  }
}
