import { Component, HostListener, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../Gestion-Utilisateur/services/auth.service';
import { User } from '../../Gestion-Utilisateur/models/user.model';

@Component({
  selector: 'app-frontoffice-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './frontoffice-layout.component.html',
  styleUrls: ['./frontoffice-layout.component.scss']
})
export class FrontofficeLayoutComponent implements OnInit {
  dropdownOpen = false;
  user: User | null = null;

  constructor(public auth: AuthService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.auth.user$.subscribe(u => {
      this.user = u;
      this.cdr.markForCheck();
    });
  }

  get initials(): string {
    const p = this.user?.prenom?.trim().charAt(0) ?? '';
    const n = this.user?.nom?.trim().charAt(0) ?? '';
    return `${p}${n}`.toUpperCase() || '?';
  }

  toggleDropdown(event?: Event) {
    if (event) event.stopPropagation();
    this.dropdownOpen = !this.dropdownOpen;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.user-avatar') && !target.closest('.dropdown-menu')) {
      this.dropdownOpen = false;
    }
  }

  logout() {
    this.dropdownOpen = false;
    this.auth.logout();
  }
}
