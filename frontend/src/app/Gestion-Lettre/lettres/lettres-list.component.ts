import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { LettreService } from '../services/lettre.service';
import { Lettre } from '../models/lettre.model';

/** Liste des lettres de motivation de l'utilisateur connecté (consultation + suppression). */
@Component({
  selector: 'app-lettres-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './lettres-list.component.html',
  styleUrl: './lettres-list.component.scss'
})
export class LettresListComponent implements OnInit {
  loading = true;
  error: string | null = null;
  actionMessage: { type: 'success' | 'danger'; text: string } | null = null;

  lettres: Lettre[] = [];
  filtered: Lettre[] = [];
  search = '';

  lettreToDelete: Lettre | null = null;
  deleting = false;

  constructor(private lettreService: LettreService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.lettreService.getMine().subscribe({
      next: (res) => {
        this.lettres = res.data ?? [];
        this.applyFilter();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Impossible de charger vos lettres de motivation.';
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
          (l.poste ?? '').toLowerCase().includes(q) ||
          l.contenu.toLowerCase().includes(q));
  }

  excerpt(text: string, max = 170): string {
    const t = (text ?? '').replace(/\s+/g, ' ').trim();
    return t.length > max ? t.slice(0, max).trimEnd() + '…' : t;
  }

  askDelete(l: Lettre): void {
    this.lettreToDelete = l;
  }

  confirmDelete(): void {
    const l = this.lettreToDelete;
    if (!l) return;
    this.deleting = true;
    this.lettreService.delete(l.id).subscribe({
      next: (res) => {
        this.deleting = false;
        this.lettreToDelete = null;
        if (res.success) {
          this.lettres = this.lettres.filter(x => x.id !== l.id);
          this.applyFilter();
          this.actionMessage = { type: 'success', text: res.message };
        } else {
          this.actionMessage = { type: 'danger', text: res.message };
        }
        this.cdr.detectChanges();
      },
      error: () => {
        this.deleting = false;
        this.lettreToDelete = null;
        this.actionMessage = { type: 'danger', text: 'Impossible de supprimer la lettre.' };
        this.cdr.detectChanges();
      }
    });
  }
}
