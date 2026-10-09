// Angular import
import { Component, output } from '@angular/core';
import { Router, RouterModule } from '@angular/router';

// project import

import { NavContentComponent } from './nav-content/nav-content.component';
import { AuthService } from 'src/app/Gestion-Utilisateur/services/auth.service';

@Component({
  selector: 'app-navigation',
  imports: [NavContentComponent, RouterModule],
  templateUrl: './navigation.component.html',
  styleUrl: './navigation.component.scss'
})
export class NavigationComponent {
  // public props
  NavCollapsedMob = output();
  SubmenuCollapse = output();
  navCollapsedMob = false;
  windowWidth = window.innerWidth;
  themeMode!: string;

  constructor(private auth: AuthService, private router: Router) {}

  // public method
  navCollapseMob() {
    if (this.windowWidth < 1025) {
      this.NavCollapsedMob.emit();
    }
  }

  navSubmenuCollapse() {
    document.querySelector('app-navigation.coded-navbar')?.classList.add('coded-trigger');
  }

  logout() {
    this.auth.logout();
  }
}