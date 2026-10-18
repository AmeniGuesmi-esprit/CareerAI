import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { InterviewService } from '../interview.service';
import {
  KnowledgeDoc, KnowledgeOverview, KnowledgeSearchHit, KnowledgeSearchResult
} from '../models/interview.model';

const DOC_TITLES: Record<string, string> = {
  'backend-java-spring': 'Questions Java & Spring Boot',
  'frontend-angular': 'Questions Angular & TypeScript',
  'questions-comportementales': 'Questions comportementales',
  'methode-star': 'Méthode STAR',
  'grille-evaluation': "Grille d'évaluation"
};

const TRACK_LABELS: Record<string, string> = {
  'backend-java-spring': 'Backend Java / Spring',
  'frontend-angular': 'Frontend Angular',
  generic: 'Générique'
};

@Component({
  selector: 'app-interview-knowledge',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './interview-knowledge.component.html',
  styleUrls: ['./interview-knowledge.component.scss']
})
export class InterviewKnowledgeComponent implements OnInit {
  private readonly api = inject(InterviewService);

  overview: KnowledgeOverview | null = null;
  error = '';

  query = '';
  track = '';
  docType = '';
  searching = false;
  result: KnowledgeSearchResult | null = null;
  searched = false;

  readonly examples = [
    'différence entre HashMap et TreeMap',
    'signals et change detection Angular',
    'conflit avec un collègue',
    'sécurité JWT et IDOR',
    'Docker compose healthcheck'
  ];

  ngOnInit() {
    this.api.knowledgeOverview().subscribe({
      next: o => (this.overview = o),
      error: err => (this.error = err.error?.message ?? 'Base de connaissances indisponible.')
    });
  }

  title(doc: KnowledgeDoc): string {
    return DOC_TITLES[doc.docId] ?? doc.docId;
  }

  trackLabel(track: string): string {
    return TRACK_LABELS[track] ?? track;
  }

  docLabel(type: string): string {
    return type === 'rubric' ? 'Grille' : type === 'guide' ? 'Méthode' : 'Question type';
  }

  get trackEntries(): { key: string; count: number }[] {
    return Object.entries(this.overview?.itemsByTrack ?? {}).map(([key, count]) => ({ key, count }));
  }

  get maxScore(): number {
    return Math.max(...(this.result?.hits.map(h => h.score) ?? [0]), 0.0001);
  }

  barWidth(hit: KnowledgeSearchHit): number {
    return Math.round((hit.score / this.maxScore) * 100);
  }

  search(example?: string) {
    if (example) this.query = example;
    if (!this.query.trim() || this.searching) return;
    this.searching = true;
    this.api.knowledgeSearch({
      query: this.query.trim(),
      track: this.track || undefined,
      docType: this.docType || undefined,
      k: 8
    }).subscribe({
      next: r => {
        this.result = r;
        this.searching = false;
        this.searched = true;
      },
      error: err => {
        this.error = err.error?.message ?? 'Recherche impossible.';
        this.searching = false;
      }
    });
  }

  explain(hit: KnowledgeSearchHit): string {
    const parts: string[] = [];
    if (hit.denseRank !== null) parts.push(`proche du sens de votre requête (vecteur, rang ${hit.denseRank})`);
    if (hit.bm25Rank !== null) parts.push(`contient vos mots-clés (rang ${hit.bm25Rank})`);
    return parts.length ? 'Retenue car elle ' + parts.join(' et ') + '.' : '';
  }
}
