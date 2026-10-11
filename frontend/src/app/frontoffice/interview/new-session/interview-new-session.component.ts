import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { InterviewService } from '../interview.service';
import { CvSummary } from '../models/interview.model';

@Component({
  selector: 'app-interview-new-session',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './interview-new-session.component.html',
  styleUrls: ['./interview-new-session.component.scss']
})
export class InterviewNewSessionComponent implements OnInit {
  private readonly api = inject(InterviewService);
  private readonly router = inject(Router);

  cvs: CvSummary[] = [];
  cvId: number | null = null;
  jobTitle = '';
  jobDescription = '';
  questionCount = 6;
  analysisId: number | null = null;
  submitting = false;
  error = '';

  ngOnInit() {
    this.api.listCvs(0, 50).subscribe({
      next: page => (this.cvs = page.content),
      error: err => (this.error = err.error?.message ?? 'Impossible de charger vos CV.')
    });
  }

  get valid(): boolean {
    return !!this.cvId && this.jobTitle.trim().length >= 2 && this.jobDescription.trim().length >= 50
      && this.questionCount >= 3 && this.questionCount <= 10;
  }

  submit() {
    if (!this.valid || this.submitting) return;
    this.submitting = true;
    this.error = '';
    this.api.createSession({
      cvId: this.cvId!,
      jobTitle: this.jobTitle.trim(),
      jobDescription: this.jobDescription.trim(),
      questionCount: this.questionCount,
      ...(this.analysisId ? { analysisId: this.analysisId } : {})
    }).subscribe({
      next: session => this.router.navigate(['/frontoffice/interview', session.id]),
      error: err => {
        this.error = err.error?.message ?? 'Impossible de créer la session.';
        this.submitting = false;
      }
    });
  }
}
