import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { LettreService } from '../services/lettre.service';
import { Lettre } from '../models/lettre.model';

/** Consultation d'une lettre : texte, CV associé, copier / télécharger / modifier / supprimer. */
@Component({
  selector: 'app-lettre-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './lettre-detail.component.html',
  styleUrl: './lettre-detail.component.scss'
})
export class LettreDetailComponent implements OnInit {
  loading = true;
  error: string | null = null;
  lettre: Lettre | null = null;

  showCv = false;
  copied = false;

  showDelete = false;
  deleting = false;
  deleteError = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private lettreService: LettreService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.lettreService.getById(id).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.lettre = res.data;
        } else {
          this.error = res.message || 'Lettre introuvable.';
        }
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Impossible de charger la lettre.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  copy(): void {
    if (!this.lettre) return;
    navigator.clipboard.writeText(this.lettre.contenu).then(() => {
      this.copied = true;
      this.cdr.detectChanges();
      setTimeout(() => { this.copied = false; this.cdr.detectChanges(); }, 2000);
    });
  }

  download(): void {
    if (!this.lettre) return;
    const slug = this.lettre.nomEntreprise
      .normalize('NFD').replace(/[̀-ͯ]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase() || 'lettre';
    const blob = new Blob([this.lettre.contenu], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `lettre-motivation-${slug}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  confirmDelete(): void {
    if (!this.lettre) return;
    this.deleting = true;
    this.deleteError = '';
    this.lettreService.delete(this.lettre.id).subscribe({
      next: (res) => {
        this.deleting = false;
        if (res.success) {
          this.router.navigate(['/frontoffice/lettres']);
        } else {
          this.deleteError = res.message;
          this.cdr.detectChanges();
        }
      },
      error: () => {
        this.deleting = false;
        this.deleteError = 'Impossible de supprimer la lettre.';
        this.cdr.detectChanges();
      }
    });
  }
}
