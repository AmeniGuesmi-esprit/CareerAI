import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
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
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  user: User | null = null;

  // Mêmes modules, dans le même ordre et avec les mêmes icônes que la barre de navigation
  tools: Tool[] = [
    {
      icon: 'ti-mail-opened',
      title: 'Lettres',
      description: 'Générez ou améliorez vos lettres de motivation avec l\'IA, une par entreprise.',
      available: true,
      route: '/frontoffice/lettres'
    },
    {
      icon: 'ti-school',
      title: 'Formations',
      description: 'Gérez vos diplômes et formations : établissement, domaine et dates.',
      available: true,
      route: '/frontoffice/formations'
    },
    {
      icon: 'ti-bulb',
      title: 'Compétences',
      description: 'Listez vos compétences par catégorie et par niveau de maîtrise.',
      available: true,
      route: '/frontoffice/competences'
    },
    {
      icon: 'ti-compass',
      title: 'Orientation',
      description: 'Demandez conseil à l\'IA sur votre parcours, vos formations et vos certifications.',
      available: true,
      route: '/frontoffice/orientation'
    },
    {
      icon: 'ti-briefcase',
      title: 'Mes expériences',
      description: 'Gérez vos expériences et générez votre pitch « Présentez-vous » avec l\'IA.',
      available: true,
      route: '/frontoffice/experiences'
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
      title: 'Matching CV',
      description: 'Mesurez la compatibilité entre votre CV et une offre avec un score sur 100.',
      available: true,
      route: '/frontoffice/compatibility'
    },
    {
      icon: 'ti-messages',
      title: 'Entretien',
      description: 'Entraînez-vous avec des questions d\'entretien et recevez un feedback noté.',
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