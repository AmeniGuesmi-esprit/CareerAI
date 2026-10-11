import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnalysisListItem, scoreLevel } from '../compatibility.models';
import { CompatibilityService, apiErrorMessage } from '../compatibility.service';

@Component({
  selector: 'app-analyse-history',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './analyse-history.component.html',
  styleUrls: ['./analyse-history.component.scss']
})
export class AnalyseHistoryComponent implements OnInit {
  private readonly service = inject(CompatibilityService);
  private readonly pageSize = 10;

  readonly items = signal<AnalysisListItem[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly confirmId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly levelOf = scoreLevel;

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list(page, this.pageSize).subscribe({
      next: p => {
        // Page devenue vide (ex. dernier élément supprimé) : on recule d'une page
        if (p.content.length === 0 && p.number > 0) {
          this.load(p.number - 1);
          return;
        }
        this.items.set(p.content);
        this.page.set(p.number);
        this.totalPages.set(p.totalPages);
        this.totalElements.set(p.totalElements);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  previous(): void {
    if (this.page() > 0) this.load(this.page() - 1);
  }

  next(): void {
    if (this.page() + 1 < this.totalPages()) this.load(this.page() + 1);
  }

  remove(id: number): void {
    this.deletingId.set(id);
    this.service.delete(id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.confirmId.set(null);
        this.load(this.page());
      },
      error: err => {
        this.deletingId.set(null);
        this.confirmId.set(null);
        this.error.set(apiErrorMessage(err));
      }
    });
  }
}