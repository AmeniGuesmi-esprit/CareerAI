import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService } from 'src/app/Gestion-Utilisateur/services/auth.service';

@Component({
  selector: 'app-nav-right',
  imports: [RouterModule, SharedModule],
  templateUrl: './nav-right.component.html',
  styleUrls: ['./nav-right.component.scss']
})
export class NavRightComponent {
  constructor(private auth: AuthService) {}

  get fullName(): string {
    const user = this.auth.getCurrentUser();
    return user ? `${user.prenom} ${user.nom}` : '';
  }

  get initials(): string {
    const user = this.auth.getCurrentUser();
    return `${user?.prenom?.trim().charAt(0) ?? ''}${user?.nom?.trim().charAt(0) ?? ''}`.toUpperCase() || '?';
  }
}
