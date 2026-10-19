import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LettreService } from '../services/lettre.service';
import { Lettre } from '../models/lettre.model';

/** Backoffice (ADMIN) : toutes les lettres de motivation, consultation et suppression. */
@Component({
  selector: 'app-admin-lettres',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-lettres.component.html',
  styleUrl: './admin-lettres.component.scss'
})
export class AdminLettresComponent implements OnInit {
  loading = true;
  error: string | null = null;
  actionMessage: { type: 'success' | 'danger'; text: string } | null = null;

  lettres: Lettre[] = [];
  filtered: Lettre[] = [];
  search = '';

  currentPage = 1;
  pageSize = 8;

  selected: Lettre | null = null;
  lettreToDelete: Lettre | null = null;

  constructor(private lettreService: LettreService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.lettreService.adminGetAll().subscribe({
      next: (res) => {
        this.lettres = [...(res.data ?? [])].sort((a, b) => b.id - a.id);
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Impossible de charger les lettres de motivation.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  applyFilter(): void {
    const q = this.search.trim().toLowerCase();
    this.filtered = !q
      ? [...this.lettres]
      : this.lettres.filter(l =>
          l.nomEntreprise.toLowerCase().includes(q) ||
          l.userEmail.toLowerCase().includes(q) ||
          (l.poste ?? '').toLowerCase().includes(q));
    this.currentPage = 1;
  }

  get paged(): Lettre[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filtered.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  goToPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.currentPage = p;
  }

  confirmDelete(): void {
    const l = this.lettreToDelete;
    if (!l) return;
    this.lettreService.adminDelete(l.id).subscribe({
      next: (res) => {
        this.lettreToDelete = null;
        if (res.success) {
          this.lettres = this.lettres.filter(x => x.id !== l.id);
          this.applyFilter();
        }
        this.actionMessage = { type: res.success ? 'success' : 'danger', text: res.message };
        this.cdr.detectChanges();
      },
      error: () => {
        this.lettreToDelete = null;
        this.actionMessage = { type: 'danger', text: 'Impossible de supprimer la lettre.' };
        this.cdr.detectChanges();
      }
    });
  }
}
