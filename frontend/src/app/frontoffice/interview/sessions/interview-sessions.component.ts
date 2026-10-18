import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { InterviewService } from '../interview.service';
import { SessionSummary } from '../models/interview.model';

@Component({
  selector: 'app-interview-sessions',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './interview-sessions.component.html',
  styleUrls: ['./interview-sessions.component.scss']
})
export class InterviewSessionsComponent implements OnInit {
  private readonly api = inject(InterviewService);

  sessions: SessionSummary[] = [];
  loading = false;
  error = '';

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.api.listSessions().subscribe({
      next: page => {
        this.sessions = page.content;
        this.loading = false;
      },
      error: err => {
        this.error = err.error?.message ?? 'Impossible de charger les sessions.';
        this.loading = false;
      }
    });
  }

  statusLabel(status: SessionSummary['status']): string {
    return ({ GENERATING: 'Génération en cours', READY: 'Prête', FAILED: 'Échec' } as const)[status];
  }
}
