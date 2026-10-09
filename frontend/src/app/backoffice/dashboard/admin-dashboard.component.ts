import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgApexchartsModule, ApexOptions } from 'ng-apexcharts';
import { AdminService } from '../../Gestion-Utilisateur/services/admin.service';
import { User } from '../../Gestion-Utilisateur/models/user.model';

const MONTH_LABELS_FR = ['Janv.', 'Fév.', 'Mars', 'Avr.', 'Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.', 'Nov.', 'Déc.'];

/** Animation Apex commune, réutilisée sur tous les graphiques pour un rendu vivant à l'entrée */
const LIVELY_ANIMATIONS = {
  enabled: true,
  easing: 'easeinout' as const,
  speed: 750,
  animateGradually: { enabled: true, delay: 120 },
  dynamicAnimation: { enabled: true, speed: 350 }
};

/** Légère lueur (glow) commune pour donner du relief aux graphiques */
const LIVELY_DROPSHADOW = {
  enabled: true,
  top: 4,
  left: 0,
  blur: 6,
  opacity: 0.18
};

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, NgApexchartsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {
  loading = true;
  error: string | null = null;

  totalUsers = 0;
  totalAdmins = 0;
  totalStandardUsers = 0;
  verifiedUsers = 0;
  disabledUsers = 0;

  // Répartition par rôle (donut)
  usersByRoleChart: Partial<ApexOptions> = {};

  // Comptes créés par mois (histogramme)
  usersByMonthChart: Partial<ApexOptions> = {};

  constructor(
    private adminService: AdminService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.loading = true;
    this.error = null;

    this.adminService.getAllUsers().subscribe({
      next: (res) => {
        const allUsers: User[] = res.data ?? [];

        this.totalUsers = allUsers.length;
        this.totalAdmins = allUsers.filter(u => u.role === 'ADMIN').length;
        this.totalStandardUsers = allUsers.filter(u => u.role === 'USER').length;
        this.verifiedUsers = allUsers.filter(u => u.emailVerified).length;
        this.disabledUsers = allUsers.filter(u => !u.enabled).length;

        this.buildUsersByRoleChart();
        this.buildUsersByMonthChart(allUsers);

        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = 'Impossible de charger les statistiques de la plateforme.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  /** Génère les buckets "année-mois" -> 0 pour une fenêtre glissante de N mois, avec leurs libellés */
  private buildLastMonthsBuckets(months: number): { buckets: Map<string, number>; categories: string[] } {
    const now = new Date();
    const buckets = new Map<string, number>();
    const categories: string[] = [];

    for (let i = months - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      buckets.set(`${d.getFullYear()}-${d.getMonth()}`, 0);
      categories.push(`${MONTH_LABELS_FR[d.getMonth()]} ${d.getFullYear()}`);
    }

    return { buckets, categories };
  }

  /** Compte les éléments d'une liste par mois de création */
  private countByMonth(buckets: Map<string, number>, dates: (string | undefined)[]): number[] {
    dates.forEach(dateStr => {
      const created = dateStr ? new Date(dateStr) : null;
      if (!created || isNaN(created.getTime())) return;
      const key = `${created.getFullYear()}-${created.getMonth()}`;
      if (buckets.has(key)) {
        buckets.set(key, (buckets.get(key) ?? 0) + 1);
      }
    });
    return Array.from(buckets.values());
  }

  /** Donut de répartition des comptes par rôle */
  private buildUsersByRoleChart(): void {
    this.usersByRoleChart = {
      series: [this.totalAdmins, this.totalStandardUsers],
      chart: {
        type: 'donut',
        height: 320,
        background: 'transparent',
        animations: LIVELY_ANIMATIONS,
        dropShadow: LIVELY_DROPSHADOW
      },
      labels: ['Administrateurs', 'Utilisateurs'],
      colors: ['#7C4DFF', '#00E676'],
      fill: {
        type: 'gradient',
        gradient: {
          shade: 'light',
          type: 'diagonal1',
          shadeIntensity: 0.5,
          gradientToColors: ['#448AFF', '#1DE9B6'],
          opacityFrom: 1,
          opacityTo: 0.9,
          stops: [0, 100]
        }
      },
      stroke: { width: 0 },
      legend: { position: 'bottom' },
      dataLabels: {
        enabled: true,
        style: { fontWeight: 600 },
        formatter: (val: number) => `${val.toFixed(0)}%`
      },
      plotOptions: {
        pie: {
          expandOnClick: true,
          donut: {
            labels: {
              show: true,
              total: {
                show: true,
                label: 'Comptes',
                formatter: () => this.totalUsers.toString()
              }
            }
          }
        }
      },
      tooltip: { theme: 'light' }
    };
  }

  /** Histogramme des comptes créés par mois (12 derniers mois) */
  private buildUsersByMonthChart(allUsers: User[]): void {
    const { buckets, categories } = this.buildLastMonthsBuckets(12);
    const data = this.countByMonth(buckets, allUsers.map(u => u.createdAt));

    this.usersByMonthChart = {
      series: [{ name: 'Comptes créés', data }],
      chart: {
        type: 'bar',
        height: 320,
        toolbar: { show: false },
        background: 'transparent',
        animations: LIVELY_ANIMATIONS,
        dropShadow: LIVELY_DROPSHADOW
      },
      dataLabels: { enabled: false },
      colors: ['#00C853'],
      fill: {
        type: 'gradient',
        gradient: {
          shade: 'light',
          type: 'vertical',
          shadeIntensity: 0.4,
          gradientToColors: ['#69F0AE'],
          opacityFrom: 1,
          opacityTo: 0.75,
          stops: [0, 100]
        }
      },
      plotOptions: {
        bar: { columnWidth: '45%', borderRadius: 6 }
      },
      xaxis: {
        categories,
        labels: { rotate: -45 }
      },
      yaxis: {
        min: 0,
        forceNiceScale: true,
        labels: { formatter: (val: number) => Math.round(val).toString() }
      },
      grid: { strokeDashArray: 4 },
      tooltip: { theme: 'light' }
    };
  }
}
