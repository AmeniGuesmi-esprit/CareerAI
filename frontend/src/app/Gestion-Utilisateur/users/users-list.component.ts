import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../services/admin.service';
import { AuthService } from '../services/auth.service';
import { User, Role } from '../models/user.model';

type SortField = 'id' | 'name' | 'email' | 'role';

@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './users-list.component.html',
  styleUrl: './users-list.component.scss'
})
export class UsersListComponent implements OnInit {
  loading = true;
  error: string | null = null;
  /** Message d'erreur/succès d'une action (rôle, activation, suppression, création) */
  actionMessage: { type: 'success' | 'danger'; text: string } | null = null;

  allUsers: User[] = [];
  filteredUsers: User[] = [];

  search = '';
  roleFilter: Role | 'ALL' = 'ALL';

  sortField: SortField = 'id';
  sortDirection: 'asc' | 'desc' = 'desc';

  currentPage = 1;
  pageSize = 8;

  // Modale de création
  showCreate = false;
  creating = false;
  createError = '';
  newUser = this.emptyNewUser();

  // Modale de suppression
  userToDelete: User | null = null;

  constructor(
    private adminService: AdminService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  private emptyNewUser() {
    return { prenom: '', nom: '', email: '', telephone: '', adresse: '', motDePasse: '', role: 'USER' as Role };
  }

  /** Un admin ne peut pas modifier / supprimer son propre compte depuis cette liste */
  isSelf(u: User): boolean {
    return u.id === this.auth.getCurrentUser()?.id;
  }

  loadUsers(): void {
    this.loading = true;
    this.error = null;
    this.adminService.getAllUsers().subscribe({
      next: (res) => {
        this.allUsers = res.data ?? [];
        this.applyFilters();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Impossible de charger la liste des utilisateurs.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Filtres / tri / pagination ─────────────────────────────────────────────

  applyFilters(): void {
    const q = this.search.trim().toLowerCase();
    const result = this.allUsers.filter(u => {
      const matchesRole = this.roleFilter === 'ALL' || u.role === this.roleFilter;
      const matchesSearch = !q ||
        `${u.prenom} ${u.nom}`.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q);
      return matchesRole && matchesSearch;
    });
    this.filteredUsers = this.sortUsers(result);
    this.currentPage = 1;
  }

  private sortUsers(users: User[]): User[] {
    const dir = this.sortDirection === 'asc' ? 1 : -1;
    return [...users].sort((a, b) => {
      switch (this.sortField) {
        case 'name':
          return `${a.prenom} ${a.nom}`.localeCompare(`${b.prenom} ${b.nom}`) * dir;
        case 'email':
          return a.email.localeCompare(b.email) * dir;
        case 'role':
          return a.role.localeCompare(b.role) * dir;
        default:
          return (a.id - b.id) * dir;
      }
    });
  }

  toggleSort(field: SortField): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }
    this.filteredUsers = this.sortUsers(this.filteredUsers);
  }

  get pagedUsers(): User[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredUsers.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredUsers.length / this.pageSize));
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  setRoleFilter(role: Role | 'ALL'): void {
    this.roleFilter = role;
    this.applyFilters();
  }

  countByRole(role: Role): number {
    return this.allUsers.filter(u => u.role === role).length;
  }

  getInitials(u: User): string {
    const first = u.prenom?.trim()?.charAt(0) ?? '';
    const last = u.nom?.trim()?.charAt(0) ?? '';
    return `${first}${last}`.toUpperCase() || '?';
  }

  // ── Actions ADMIN ──────────────────────────────────────────────────────────

  private replaceUser(updated: User): void {
    this.allUsers = this.allUsers.map(u => u.id === updated.id ? updated : u);
    this.applyFilters();
  }

  private notify(type: 'success' | 'danger', text: string): void {
    this.actionMessage = { type, text };
    this.cdr.detectChanges();
  }

  onRoleChange(u: User, role: Role): void {
    if (role === u.role) return;
    this.adminService.changeRole(u.id, role).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.replaceUser(res.data);
          this.notify('success', res.message);
        } else {
          this.notify('danger', res.message);
          this.loadUsers(); // remet le <select> sur le rôle réel
        }
      },
      error: () => {
        this.notify('danger', 'Impossible de modifier le rôle.');
        this.loadUsers();
      }
    });
  }

  toggleEnabled(u: User): void {
    this.adminService.setEnabled(u.id, !u.enabled).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.replaceUser(res.data);
          this.notify('success', res.message);
        } else {
          this.notify('danger', res.message);
        }
      },
      error: () => this.notify('danger', 'Impossible de modifier le statut du compte.')
    });
  }

  askDelete(u: User): void {
    this.userToDelete = u;
  }

  confirmDelete(): void {
    const u = this.userToDelete;
    if (!u) return;
    this.adminService.deleteUser(u.id).subscribe({
      next: (res) => {
        this.userToDelete = null;
        if (res.success) {
          this.allUsers = this.allUsers.filter(x => x.id !== u.id);
          this.applyFilters();
          this.notify('success', res.message);
        } else {
          this.notify('danger', res.message);
        }
      },
      error: () => {
        this.userToDelete = null;
        this.notify('danger', 'Impossible de supprimer l\'utilisateur.');
      }
    });
  }

  // ── Création ───────────────────────────────────────────────────────────────

  openCreate(): void {
    this.newUser = this.emptyNewUser();
    this.createError = '';
    this.showCreate = true;
  }

  submitCreate(): void {
    const u = this.newUser;
    if (!u.prenom.trim() || !u.nom.trim() || !u.email.trim() || !u.telephone.trim() || !u.adresse.trim() || !u.motDePasse) {
      this.createError = 'Tous les champs sont obligatoires';
      return;
    }
    if (!/^[+0-9 ()-]{8,20}$/.test(u.telephone.trim())) {
      this.createError = 'Numéro de téléphone invalide';
      return;
    }
    if (u.motDePasse.length < 8) {
      this.createError = 'Le mot de passe doit contenir au moins 8 caractères';
      return;
    }

    this.creating = true;
    this.createError = '';
    this.adminService.createUser({
      ...u,
      prenom: u.prenom.trim(), nom: u.nom.trim(), email: u.email.trim(),
      telephone: u.telephone.trim(), adresse: u.adresse.trim()
    }).subscribe({
      next: (res) => {
        this.creating = false;
        if (res.success) {
          this.showCreate = false;
          this.notify('success', res.message);
          this.loadUsers();
        } else {
          this.createError = res.message;
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        this.creating = false;
        this.createError = err?.error?.message || 'Erreur lors de la création.';
        this.cdr.detectChanges();
      }
    });
  }
}
