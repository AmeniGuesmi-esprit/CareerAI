import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { OrientationComponent } from './orientation.component';
import { OrientationService } from '../services/orientation.service';
import { Advice } from '../models/orientation.model';

describe('OrientationComponent', () => {
  let fixture: ComponentFixture<OrientationComponent>;
  let component: OrientationComponent;
  let api: jasmine.SpyObj<OrientationService>;

  const LONG = 'Je suis étudiant en informatique, quelles certifications cloud ?';
  const advice: Advice = { reponse: 'Voici **AWS** et **Azure**', statut: 'VERIFIEE', avertissement: null };

  beforeEach(() => {
    api = jasmine.createSpyObj<OrientationService>('OrientationService', ['advise']);
    TestBed.configureTestingModule({
      imports: [OrientationComponent],
      providers: [{ provide: OrientationService, useValue: api }]
    });
    fixture = TestBed.createComponent(OrientationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('état initial vide', () => {
    expect(component.advice()).toBeNull();
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBe('');
  });

  it('refuse un prompt trop court sans appeler l\'API', () => {
    component.prompt = 'trop court';
    component.envoyer();

    expect(component.error()).toContain('trop court');
    expect(api.advise).not.toHaveBeenCalled();
  });

  it('un prompt fait uniquement d\'espaces est refusé', () => {
    component.prompt = ' '.repeat(50);
    component.envoyer();

    expect(api.advise).not.toHaveBeenCalled();
  });

  it('accepte un prompt de exactement 20 caractères', () => {
    api.advise.and.returnValue(of({ success: true, message: '', data: advice }));
    component.prompt = 'a'.repeat(20);
    component.envoyer();

    expect(api.advise).toHaveBeenCalledTimes(1);
  });

  it('envoie le prompt et affiche le conseil', () => {
    api.advise.and.returnValue(of({ success: true, message: '', data: advice }));
    component.prompt = LONG;
    component.envoyer();

    expect(api.advise).toHaveBeenCalledWith(LONG);
    expect(component.advice()).toEqual(advice);
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBe('');
  });

  it('loading pendant l\'appel + un 2e envoi est ignoré', () => {
    const pending = new Subject<any>();
    api.advise.and.returnValue(pending);
    component.prompt = LONG;

    component.envoyer();
    expect(component.loading()).toBeTrue();

    component.envoyer(); // double clic
    expect(api.advise).toHaveBeenCalledTimes(1);

    pending.next({ success: true, message: '', data: advice });
    pending.complete();
    expect(component.loading()).toBeFalse();
  });

  it('efface l\'ancien conseil au nouvel envoi', () => {
    component.advice.set(advice);
    api.advise.and.returnValue(new Subject<any>());
    component.prompt = LONG;
    component.envoyer();

    expect(component.advice()).toBeNull();
  });

  it('affiche le message du backend (ex. 429)', () => {
    api.advise.and.returnValue(throwError(() => ({ status: 429, error: { message: 'Trop de demandes' } })));
    component.prompt = LONG;
    component.envoyer();

    expect(component.error()).toBe('Trop de demandes');
    expect(component.loading()).toBeFalse();
  });

  it('message par défaut si le backend ne renvoie rien', () => {
    api.advise.and.returnValue(throwError(() => ({ status: 500 })));
    component.prompt = LONG;
    component.envoyer();

    expect(component.error()).toBe('Service indisponible, réessaie.');
  });

  it('serveur injoignable (status 0)', () => {
    api.advise.and.returnValue(throwError(() => ({ status: 0 })));
    component.prompt = LONG;
    component.envoyer();

    expect(component.error()).toContain('Serveur injoignable');
  });

  describe('segments (mise en gras sans innerHTML)', () => {
    it('découpe sur **gras**', () => {
      expect(component.segments('a **b** c')).toEqual([
        { t: 'a ', bold: false }, { t: 'b', bold: true }, { t: ' c', bold: false }
      ]);
    });

    it('texte sans gras', () => {
      expect(component.segments('simple')).toEqual([{ t: 'simple', bold: false }]);
    });

    it('chaîne vide → aucun segment', () => {
      expect(component.segments('')).toEqual([]);
    });

    it('ne produit jamais de HTML interprétable (balises gardées comme texte)', () => {
      const segs = component.segments('<img src=x onerror=alert(1)> **ok**');
      expect(segs[0].bold).toBeFalse();
      expect(segs[0].t).toContain('<img');
    });
  });
});
