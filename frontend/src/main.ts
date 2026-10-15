import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { importProvidersFrom, provideZonelessChangeDetection } from '@angular/core';
import { AppRoutingModule } from './app/app-routing.module';
import { authInterceptor } from './app/Gestion-Utilisateur/interceptors/auth.interceptor';

bootstrapApplication(AppComponent, {
  providers: [
    // Angular 21 n'embarque plus zone.js par défaut : sans ce provider,
    // Angular ne relance PAS la détection de changement après un appel HTTP,
    // un setTimeout, etc. L'écran ne se met à jour que si un événement DOM
    // qu'Angular gère déjà (clic sur un élément Angular) survient ensuite
    // → d'où le "ça se charge seulement quand je clique ailleurs".
    provideZonelessChangeDetection(),
    importProvidersFrom(AppRoutingModule),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
}).catch(err => console.error(err));