import { FormBuilder } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ExperienceFormComponent } from './experience-form.component';
import { ExperienceService } from '../services/experience.service';
import { Experience, ExperienceRequest } from '../models/experience.model';

const ok = <T>(data: T) => of({ success: true, message: '', data });

const EXP: Experience = {
  id: 5, poste: 'Dev', entreprise: 'ACME', lieu: null, typeContrat: 'CDI',
  dateDebut: '2024-01-01', dateFin: null, enCours: true, description: null
};

type Patch = Partial<{
  poste: string; entreprise: string; lieu: string; typeContrat: string;
  dateDebut: string; dateFin: string; enCours: boolean; description: string;
}>;

/** Crée le composant sans TestBed : on injecte directement des faux services. */
function setup(id: string | null = null, service: Record<string, unknown> = {}) {
  const navigated: unknown[][] = [];
  const router = {
    navigate: (commands: unknown[]) => { navigated.push(commands); return Promise.resolve(true); }
  } as unknown as Router;
  const route = { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } as unknown as ActivatedRoute;
  const cmp = new ExperienceFormComponent(new FormBuilder(), service as unknown as ExperienceService, route, router);
  return { cmp, navigated };
}

/** Remplit le formulaire avec des valeurs valides (écrasables). */
function fill(cmp: ExperienceFormComponent, patch: Patch = {}) {
  cmp.form.patchValue({
    poste: 'Dev', entreprise: 'ACME', dateDebut: '2024-01-01', dateFin: '2024-06-01', ...patch
  });
}

describe('ExperienceFormComponent', () => {
  describe('validation', () => {
    it('est invalide au départ', () => {
      expect(setup().cmp.form.invalid).toBe(true);
    });

    it('est valide une fois rempli', () => {
      const { cmp } = setup();
      fill(cmp);
      expect(cmp.form.valid).toBe(true);
    });

    it('exige poste, entreprise et date de début', () => {
      const { cmp } = setup();
      fill(cmp, { poste: '' });
      expect(cmp.form.get('poste')?.hasError('required')).toBe(true);
      fill(cmp, { poste: 'Dev', entreprise: '' });
      expect(cmp.form.get('entreprise')?.hasError('required')).toBe(true);
      fill(cmp, { entreprise: 'ACME', dateDebut: '' });
      expect(cmp.form.get('dateDebut')?.hasError('required')).toBe(true);
    });

    it('exige une date de fin sauf si "en cours"', () => {
      const { cmp } = setup();
      fill(cmp, { dateFin: '' });
      expect(cmp.form.errors).toEqual({ finRequise: true });
      fill(cmp, { dateFin: '', enCours: true });
      expect(cmp.form.errors).toBeNull();
    });

    it('refuse une date de fin avant le début', () => {
      const { cmp } = setup();
      fill(cmp, { dateFin: '2023-12-31' });
      expect(cmp.form.errors).toEqual({ finAvantDebut: true });
    });

    it('limite la description à 2000 caractères', () => {
      const { cmp } = setup();
      fill(cmp, { description: 'x'.repeat(2000) });
      expect(cmp.form.valid).toBe(true);
      fill(cmp, { description: 'x'.repeat(2001) });
      expect(cmp.form.get('description')?.hasError('maxlength')).toBe(true);
    });
  });

  describe('aides', () => {
    it('invalid() n\'affiche l\'erreur qu\'après contact ou soumission', () => {
      const { cmp } = setup();
      expect(cmp.invalid('poste')).toBe(false);
      cmp.submitted.set(true);
      expect(cmp.invalid('poste')).toBe(true);
    });

    it('onEnCoursChange vide la date de fin quand "en cours" est coché', () => {
      const { cmp } = setup();
      fill(cmp, { enCours: true });
      cmp.onEnCoursChange();
      expect(cmp.form.value.dateFin).toBe('');
    });

    it('descLength compte les caractères de la description', () => {
      const { cmp } = setup();
      fill(cmp, { description: 'abcd' });
      expect(cmp.descLength).toBe(4);
    });
  });

  describe('mode création', () => {
    it('n\'appelle pas getById sans id dans la route', () => {
      let called = false;
      const { cmp } = setup(null, { getById: () => { called = true; return ok(EXP); } });
      cmp.ngOnInit();
      expect(called).toBe(false);
      expect(cmp.isEdit).toBe(false);
    });

    it('ne soumet pas un formulaire invalide', () => {
      let called = false;
      const { cmp } = setup(null, { create: () => { called = true; return ok(EXP); } });
      cmp.submit();
      expect(called).toBe(false);
      expect(cmp.submitted()).toBe(true);
      expect(cmp.form.touched).toBe(true);
    });

    it('envoie un corps nettoyé puis redirige vers la liste', () => {
      let body!: ExperienceRequest;
      const { cmp, navigated } = setup(null, { create: (b: ExperienceRequest) => { body = b; return ok(EXP); } });
      fill(cmp, { poste: '  Dev  ', entreprise: ' ACME ', lieu: '   ', description: '' });
      cmp.submit();
      expect(body).toEqual({
        poste: 'Dev', entreprise: 'ACME', lieu: null, typeContrat: 'CDI',
        dateDebut: '2024-01-01', dateFin: '2024-06-01', enCours: false, description: null
      });
      expect(navigated).toEqual([['/frontoffice/experiences']]);
    });

    it('met dateFin à null quand "en cours"', () => {
      let body!: ExperienceRequest;
      const { cmp } = setup(null, { create: (b: ExperienceRequest) => { body = b; return ok(EXP); } });
      fill(cmp, { enCours: true, dateFin: '2024-06-01' });
      cmp.submit();
      expect(body.enCours).toBe(true);
      expect(body.dateFin).toBeNull();
    });

    it('affiche le message du serveur en cas d\'échec', () => {
      const err = new HttpErrorResponse({ status: 400, error: { message: 'Dates invalides' } });
      const { cmp, navigated } = setup(null, { create: () => throwError(() => err) });
      fill(cmp);
      cmp.submit();
      expect(cmp.error()).toBe('Dates invalides');
      expect(cmp.saving()).toBe(false);
      expect(navigated.length).toBe(0);
    });

    it('affiche un message par défaut si le serveur n\'en renvoie pas', () => {
      const { cmp } = setup(null, { create: () => throwError(() => new HttpErrorResponse({ status: 500 })) });
      fill(cmp);
      cmp.submit();
      expect(cmp.error()).toBe("Échec de l'enregistrement. Réessayez.");
    });
  });

  describe('mode édition', () => {
    it('charge l\'expérience et pré-remplit le formulaire', () => {
      const { cmp } = setup('5', { getById: () => ok(EXP) });
      cmp.ngOnInit();
      expect(cmp.isEdit).toBe(true);
      expect(cmp.editId).toBe(5);
      expect(cmp.loading()).toBe(false);
      expect(cmp.form.value.poste).toBe('Dev');
      expect(cmp.form.value.lieu).toBe(''); // null -> ''
      expect(cmp.form.value.enCours).toBe(true);
    });

    it('signale une expérience introuvable', () => {
      const { cmp } = setup('5', { getById: () => throwError(() => new HttpErrorResponse({ status: 404 })) });
      cmp.ngOnInit();
      expect(cmp.error()).toBe('Expérience introuvable.');
      expect(cmp.loading()).toBe(false);
    });

    it('appelle update avec l\'id à la soumission', () => {
      let updated: [number, ExperienceRequest] | undefined;
      const { cmp, navigated } = setup('5', {
        getById: () => ok(EXP),
        update: (id: number, b: ExperienceRequest) => { updated = [id, b]; return ok(EXP); }
      });
      cmp.ngOnInit();
      cmp.submit();
      expect(updated?.[0]).toBe(5);
      expect(updated?.[1].poste).toBe('Dev');
      expect(navigated).toEqual([['/frontoffice/experiences']]);
    });
  });
});
