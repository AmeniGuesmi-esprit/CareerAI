import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../Gestion-Utilisateur/services/auth.service';
import { User } from '../../Gestion-Utilisateur/models/user.model';

interface Tool {
  icon: string;
  title: string;
  description: string;
  available: boolean;
  route?: string; // à renseigner quand le module devient disponible
}

/**
 * Page "Accueil" du frontoffice (USER) : message de bienvenue + outils de préparation.
 * Pour activer un outil : passer `available` à true et lui ajouter une route.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  user: User | null = null;

  tools: Tool[] = [
    {
      icon: 'ti-file-cv',
      title: 'Générer mon CV',
      description: 'Créez un CV professionnel adapté à l\'offre visée.',
      available: false
    },
    {
      icon: 'ti-mail-opened',
      title: 'Lettre de motivation',
      description: 'Une lettre personnalisée à partir de votre profil et de l\'offre.',
      available: false
    },
    {
      icon: 'ti-target-arrow',
      title: 'Analyse CV / offre',
      description: 'Découvrez les compétences à améliorer pour décrocher une offre d\'emploi grâce à l\'IA.',
      available: true,
      route: '/frontoffice/skill-gap'
    },
    {
      icon: 'ti-chart-donut',
      title: 'Score de matching',
      description: 'Mesurez la compatibilité entre votre CV et une description d\'offre.',
      available: true,
      route: '/frontoffice/compatibility'
    },
    {
      icon: 'ti-messages',
      title: 'Questions d\'entretien',
      description: 'Préparez-vous avec les questions les plus probables pour le poste.',
      available: true,
      route: '/frontoffice/interview'
    }
  ];

  ngOnInit() {
    this.user = this.auth.getCurrentUser();
    this.auth.user$.subscribe(u => (this.user = u));
  }

  open(tool: Tool) {
    if (tool.available && tool.route) {
      this.router.navigateByUrl(tool.route);
    }
  }
}
