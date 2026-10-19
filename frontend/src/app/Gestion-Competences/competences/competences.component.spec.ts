import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CompetencesComponent } from './competences.component';
import { CompetenceService } from '../services/competence.service';
import { Competence } from '../models/competence.model';

describe('CompetencesComponent', () => {
  let fixture: ComponentFixture<CompetencesComponent>;
  let component: CompetencesComponent;
  let api: jasmine.SpyObj<CompetenceService>;

  const data: Competence[] = [
    { id: 1, nom: 'Java', niveau: 'AVANCE', categorie: 'TECHNIQUE' },
    { id: 2, nom: 'Anglais', niveau: 'INTERMEDIAIRE', categorie: 'LANGUE' },
    { id: 3, nom: 'Docker', niveau: 'DEBUTANT', categorie: 'OUTIL' }
  ];
  const ok = <T>(d: T, message = 'ok') => of({ success: true, message, data: d });

  beforeEach(() => {
    jasmine.clock().install();
    api = jasmine.createSpyObj<CompetenceService>('CompetenceService',
      ['getCompetences', 'createCompetence', 'updateCompetence', 'deleteCompetence']);
    api.getCompetences.and.returnValue(ok(data));

    TestBed.configureTestingModule({
      imports: [CompetencesComponent],
      providers: [{ provide: CompetenceService, useValue: api }]
    });
    fixture = TestBed.createComponent(CompetencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges(); // ngOnInit → load()
  });

  afterEach(() => jasmine.clock().uninstall());

  it('charge les compétences au démarrage', () => {
    expect(api.getCompetences).toHaveBeenCalledTimes(1);
    expect(component.competences()).toEqual(data);
    expect(component.loading()).toBeFalse();
  });

  it('affiche une erreur si le chargement échoue', () => {
    api.getCompetences.and.returnValue(throwError(() => ({ status: 500, error: { message: 'Boom' } })));
    component.load();

    expect(component.loading()).toBeFalse();
    expect(component.pageError()).toBe('Boom');
  });

  it('message dédié si le serveur est injoignable (status 0)', () => {
    api.getCompetences.and.returnValue(throwError(() => ({ status: 0 })));
    component.load();

    expect(component.pageError()).toContain('Serveur injoignable');
  });

  it('tolère une réponse sans data', () => {
    api.getCompetences.and.returnValue(of({ success: true, message: '', data: null as any }));
    component.load();

    expect(component.competences()).toEqual([]);
  });

  it('filtre par catégorie', () => {
    component.filter.set('LANGUE');
    expect(component.filtered().map(c => c.nom)).toEqual(['Anglais']);

    component.filter.set('ALL');
    expect(component.filtered().length).toBe(3);
  });

  it('libellés et rangs des niveaux / catégories', () => {
    expect(component.niveauLabel('EXPERT')).toBe('Expert');
    expect(component.niveauLabel('INCONNU')).toBe('INCONNU');
    expect(component.niveauRank('AVANCE')).toBe(3);
    expect(component.niveauRank('INCONNU')).toBe(0);
    expect(component.categorieLabel('SOFT_SKILL')).toBe('Soft skill');
  });

  it('openCreate réinitialise le formulaire', () => {
    component.editingId = 99;
    component.openCreate();

    expect(component.showForm).toBeTrue();
    expect(component.editingId).toBeNull();
    expect(component.form).toEqual({ nom: '', niveau: 'INTERMEDIAIRE', categorie: 'TECHNIQUE' });
  });

  it('openEdit pré-remplit le formulaire', () => {
    component.openEdit(data[0]);

    expect(component.editingId).toBe(1);
    expect(component.form).toEqual({ nom: 'Java', niveau: 'AVANCE', categorie: 'TECHNIQUE' });
    expect(component.showForm).toBeTrue();
  });

  it('closeForm masque le formulaire', () => {
    component.openCreate();
    component.closeForm();
    expect(component.showForm).toBeFalse();
  });

  it('save refuse un nom vide sans appeler l\'API', () => {
    component.openCreate();
    component.form.nom = '   ';
    component.save();

    expect(component.formError()).toContain('obligatoire');
    expect(api.createCompetence).not.toHaveBeenCalled();
  });

  it('save (création) envoie le nom trimé puis recharge la liste', () => {
    api.createCompetence.and.returnValue(ok(data[0], 'Compétence ajoutée'));
    component.openCreate();
    component.form.nom = '  Kubernetes  ';
    component.save();

    expect(api.createCompetence).toHaveBeenCalledWith({ nom: 'Kubernetes', niveau: 'INTERMEDIAIRE', categorie: 'TECHNIQUE' });
    expect(api.getCompetences).toHaveBeenCalledTimes(2);
    expect(component.showForm).toBeFalse();
    expect(component.saving()).toBeFalse();
    expect(component.success()).toBe('Compétence ajoutée');

    jasmine.clock().tick(3000); // le toast disparaît
    expect(component.success()).toBe('');
  });

  it('save (édition) appelle updateCompetence avec l\'id', () => {
    api.updateCompetence.and.returnValue(ok(data[0]));
    component.openEdit(data[0]);
    component.form.niveau = 'EXPERT';
    component.save();

    expect(api.updateCompetence).toHaveBeenCalledWith(1, { nom: 'Java', niveau: 'EXPERT', categorie: 'TECHNIQUE' });
    expect(api.createCompetence).not.toHaveBeenCalled();
    jasmine.clock().tick(3000);
  });

  it('save affiche le message du backend en cas de conflit (409)', () => {
    api.createCompetence.and.returnValue(
      throwError(() => ({ status: 409, error: { message: 'Cette compétence existe déjà' } })));
    component.openCreate();
    component.form.nom = 'Java';
    component.save();

    expect(component.formError()).toBe('Cette compétence existe déjà');
    expect(component.saving()).toBeFalse();
    expect(component.showForm).toBeTrue(); // le formulaire reste ouvert
  });

  it('askDelete / confirmDelete supprime puis recharge', () => {
    api.deleteCompetence.and.returnValue(ok(undefined as void, 'Compétence supprimée'));
    component.askDelete(data[1]);
    expect(component.toDelete).toBe(data[1]);

    component.confirmDelete();

    expect(api.deleteCompetence).toHaveBeenCalledWith(2);
    expect(component.toDelete).toBeNull();
    expect(component.success()).toBe('Compétence supprimée');
    expect(api.getCompetences).toHaveBeenCalledTimes(2);
    jasmine.clock().tick(3000);
  });

  it('confirmDelete sans sélection ne fait rien', () => {
    component.confirmDelete();
    expect(api.deleteCompetence).not.toHaveBeenCalled();
  });

  it('confirmDelete : erreur → message de page', () => {
    api.deleteCompetence.and.returnValue(throwError(() => ({ status: 404, error: {} })));
    component.askDelete(data[0]);
    component.confirmDelete();

    expect(component.pageError()).toBe('Suppression impossible.');
    expect(component.toDelete).toBeNull();
  });
});
