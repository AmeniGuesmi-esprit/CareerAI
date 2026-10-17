import { HttpErrorResponse } from '@angular/common/http';
import { NEVER, of, throwError } from 'rxjs';
import { ExperienceListComponent } from './experience-list.component';
import { ExperienceService } from '../services/experience.service';
import { Experience } from '../models/experience.model';

const ok = <T>(data: T) => of({ success: true, message: '', data });

const make = (o: Partial<Experience> = {}): Experience => ({
  id: 1, poste: 'Développeur', entreprise: 'ACME', lieu: 'Tunis', typeContrat: 'CDI',
  dateDebut: '2024-01-15', dateFin: '2024-12-20', enCours: false, description: null, ...o
});

const DEV = make({ id: 1 });
const STAGE = make({ id: 2, poste: 'Testeur', entreprise: 'Globex', lieu: null, typeContrat: 'STAGE', enCours: true, dateFin: null });
const FREE = make({ id: 3, poste: 'Designer', entreprise: 'Initech', lieu: 'Sousse', typeContrat: 'FREELANCE' });

/** Crée le composant sans TestBed avec un faux service. */
const setup = (service: Record<string, unknown>) =>
  new ExperienceListComponent(service as unknown as ExperienceService);

/** Composant déjà chargé avec 3 expériences. */
const loaded = (extra: Record<string, unknown> = {}) => {
  const cmp = setup({ getAll: () => ok([DEV, STAGE, FREE]), ...extra });
  cmp.ngOnInit();
  return cmp;
};

describe('ExperienceListComponent', () => {
  describe('chargement', () => {
    it('charge les expériences au démarrage', () => {
      const cmp = loaded();
      expect(cmp.experiences().length).toBe(3);
      expect(cmp.loading()).toBe(false);
      expect(cmp.error()).toBe('');
    });

    it('est en chargement tant que le serveur n\'a pas répondu', () => {
      const cmp = setup({ getAll: () => NEVER });
      cmp.ngOnInit();
      expect(cmp.loading()).toBe(true);
    });

    it('tolère une réponse sans data', () => {
      const cmp = setup({ getAll: () => ok(undefined) });
      cmp.ngOnInit();
      expect(cmp.experiences()).toEqual([]);
    });

    it('affiche une erreur si le chargement échoue', () => {
      const cmp = setup({ getAll: () => throwError(() => new HttpErrorResponse({ status: 0 })) });
      cmp.ngOnInit();
      expect(cmp.error()).toContain('Impossible de charger');
      expect(cmp.loading()).toBe(false);
    });
  });

  describe('filtres', () => {
    it('sans filtre, renvoie tout', () => {
      expect(loaded().filtered().length).toBe(3);
    });

    it('recherche dans poste, entreprise et lieu (insensible à la casse)', () => {
      const cmp = loaded();
      cmp.search.set('  TESTEUR ');
      expect(cmp.filtered()).toEqual([STAGE]);
      cmp.search.set('initech');
      expect(cmp.filtered()).toEqual([FREE]);
      cmp.search.set('tunis');
      expect(cmp.filtered()).toEqual([DEV]);
    });

    it('filtre par type de contrat', () => {
      const cmp = loaded();
      cmp.typeFilter.set('STAGE');
      expect(cmp.filtered()).toEqual([STAGE]);
    });

    it('combine recherche et type', () => {
      const cmp = loaded();
      cmp.typeFilter.set('CDI');
      cmp.search.set('globex');
      expect(cmp.filtered()).toEqual([]);
    });

    it('enCoursCount compte les expériences en cours', () => {
      expect(loaded().enCoursCount()).toBe(1);
    });
  });

  describe('duree()', () => {
    const cmp = setup({});
    const d = (debut: string, fin: string) => cmp.duree(make({ dateDebut: debut, dateFin: fin }));

    it('compte le mois entamé', () => {
      expect(d('2024-01-15', '2024-03-20')).toBe('3 mois');
    });

    it('affiche années et mois', () => {
      expect(d('2024-01-15', '2025-04-20')).toBe('1 an 4 mois');
      expect(d('2024-01-15', '2026-01-20')).toBe('2 ans 1 mois');
    });

    it('n\'affiche que les années si le compte est rond', () => {
      expect(d('2024-01-15', '2025-01-10')).toBe('1 an');
    });

    it('affiche au moins 1 mois', () => {
      expect(d('2024-01-15', '2024-01-15')).toBe('1 mois');
    });

    it('utilise la date du jour quand l\'expérience est en cours', () => {
      const enCours = make({ dateDebut: '2024-01-15', dateFin: '2020-01-15', enCours: true });
      expect(cmp.duree(enCours)).toContain('an');
    });
  });

  describe('suppression', () => {
    it('askDelete puis cancelDelete', () => {
      const cmp = loaded();
      cmp.askDelete(DEV);
      expect(cmp.toDelete()).toBe(DEV);
      cmp.cancelDelete();
      expect(cmp.toDelete()).toBeNull();
    });

    it('cancelDelete est ignoré pendant la suppression', () => {
      const cmp = loaded();
      cmp.askDelete(DEV);
      cmp.deleting.set(true);
      cmp.cancelDelete();
      expect(cmp.toDelete()).toBe(DEV);
    });

    it('confirmDelete ne fait rien sans sélection', () => {
      let called = false;
      const cmp = loaded({ delete: () => { called = true; return ok(undefined); } });
      cmp.confirmDelete();
      expect(called).toBe(false);
    });

    it('confirmDelete retire l\'expérience de la liste', () => {
      let deletedId = -1;
      const cmp = loaded({ delete: (id: number) => { deletedId = id; return ok(undefined); } });
      cmp.askDelete(DEV);
      cmp.confirmDelete();
      expect(deletedId).toBe(1);
      expect(cmp.experiences().map(e => e.id)).toEqual([2, 3]);
      expect(cmp.toDelete()).toBeNull();
      expect(cmp.deleting()).toBe(false);
      expect(cmp.success()).toBe('Expérience supprimée.');
    });

    it('confirmDelete garde la liste et affiche une erreur en cas d\'échec', () => {
      const cmp = loaded({ delete: () => throwError(() => new HttpErrorResponse({ status: 500 })) });
      cmp.askDelete(DEV);
      cmp.confirmDelete();
      expect(cmp.experiences().length).toBe(3);
      expect(cmp.error()).toBe('La suppression a échoué.');
      expect(cmp.toDelete()).toBeNull();
      expect(cmp.deleting()).toBe(false);
    });
  });
});
