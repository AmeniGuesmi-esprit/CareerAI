import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { InterviewService } from '../interview.service';
import { CvSummary } from '../models/interview.model';

@Component({
  selector: 'app-interview-cvs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './interview-cvs.component.html',
  styleUrls: ['./interview-cvs.component.scss']
})
export class InterviewCvsComponent implements OnInit {
  private readonly api = inject(InterviewService);

  cvs: CvSummary[] = [];
  label = '';
  file: File | null = null;
  uploading = false;
  error = '';
  success = '';

  ngOnInit() {
    this.load();
  }

  load() {
    this.api.listCvs(0, 50).subscribe({
      next: page => (this.cvs = page.content),
      error: err => (this.error = err.error?.message ?? 'Impossible de charger vos CV.')
    });
  }

  onFileSelected(event: Event) {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload() {
    if (!this.file) return;
    this.uploading = true;
    this.error = '';
    this.success = '';
    this.api.uploadCv(this.file, this.label).subscribe({
      next: () => {
        this.success = 'CV ajouté. Les données personnelles ont été masquées.';
        this.label = '';
        this.file = null;
        this.uploading = false;
        this.load();
      },
      error: err => {
        this.error = err.error?.message ?? 'Échec de l\'envoi du CV.';
        this.uploading = false;
      }
    });
  }

  remove(cv: CvSummary) {
    if (!confirm(`Supprimer le CV « ${cv.label} » ? Les sessions existantes sont conservées.`)) return;
    this.api.deleteCv(cv.id).subscribe({
      next: () => this.load(),
      error: err => (this.error = err.error?.message ?? 'Suppression impossible.')
    });
  }
}
