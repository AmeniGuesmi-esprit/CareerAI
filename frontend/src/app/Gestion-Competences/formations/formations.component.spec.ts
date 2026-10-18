import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { FormationsComponent } from './formations.component';
import { CompetenceService } from '../services/competence.service';
import { Formation } from '../models/competence.model';

describe('FormationsComponent', () => {
  let fixture: ComponentFixture<FormationsComponent>;
  let component: FormationsComponent;
  let api: jasmine.SpyObj<CompetenceService>;

  const data: Formation[] = [
    { id: 1, diplome: 'Licence', etablissement: 'ISI', domaine: null, dateDebut: '2020-09-01', dateFin: '2023-06-30', enCours: false, description: null },
    { id: 2, diplome: 'Ingénieur', etablissement: 'ESPRIT', domaine: 'Info', dateDebut: '2023-09-01', dateFin: null, enCours: true, description: 'SAE' }
  ];
  const ok = <T>(d: T, message = 'ok') => of({ success: true, message, data: d });

  beforeEach(() => {
    jasmine.clock().install();
    api = jasmine.createSpyObj<CompetenceService>('CompetenceService',
      ['getFormations', 'createFormation', 'updateFormation', 'deleteFormation']);
    api.getFormations.and.returnValue(ok(data));

    TestBed.configureTestingModule({
      imports: [FormationsComponent],
      providers: [{ provide: CompetenceService, useValue: api }]
    });
    fixture = TestBed.createComponent(FormationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function fill(over: Partial<typeof component.form> = {}) {
    component.openCreate();
    component.form = {
      diplome: 'Master', etablissement: 'ESPRIT', domaine: '', dateDebut: '2024-09-01',
      dateFin: '2026-06-30', enCours: false, description: '', ...over
    };
  }

  afterEach(() => jasmine.clock().uninstall());

  it('charge les formations au démarrage', () => {
    expect(component.formations()).toEqual(data);
    expect(component.loading()).toBeFalse();
  });

  it('erreur de chargement → message', () => {
    api.getFormations.and.returnValue(throwError(() => ({ status: 500, error: {} })));
    component.load();

    expect(component.pageError()).toBe('Impossible de charger les formations.');
    expect(component.loading()).toBeFalse();
  });

  it('openEdit remplace les null par des chaînes vides', () => {
    component.openEdit(data[0]);

    expect(component.editingId).toBe(1);
    expect(component.form.domaine).toBe('');
    expect(component.form.description).toBe('');
    expect(component.showForm).toBeTrue();
  });

  it('onEnCoursChange efface la date de fin quand « en cours » est coché', () => {
    fill({ enCours: true, dateFin: '2026-06-30' });
    component.onEnCoursChange();
    expect(component.form.dateFin).toBe('');
  });

  it('onEnCoursChange conserve la date de fin si décoché', () => {
    fill({ enCours: false, dateFin: '2026-06-30' });
    component.onEnCoursChange();
    expect(component.form.dateFin).toBe('2026-06-30');
  });

  it('refuse un diplôme / établissement / date de début manquants', () => {
    fill({ diplome: ' ' });
    component.save();
    expect(component.formError()).toContain('obligatoires');

    fill({ etablissement: '' });
    component.save();
    expect(component.formError()).toContain('obligatoires');

    fill({ dateDebut: '' });
    component.save();
    expect(component.formError()).toContain('obligatoires');
    expect(api.createFormation).not.toHaveBeenCalled();
  });

  it('refuse l\'absence de date de fin si la formation n\'est pas en cours', () => {
    fill({ dateFin: '' });
    component.save();

    expect(component.formError()).toContain('date de fin est obligatoire');
    expect(api.createFormation).not.toHaveBeenCalled();
  });

  it('refuse une date de fin antérieure à la date de début', () => {
    fill({ dateDebut: '2024-09-01', dateFin: '2023-01-01' });
    component.save();

    expect(component.formError()).toContain('postérieure');
    expect(api.createFormation).not.toHaveBeenCalled();
  });

  it('création : normalise domaine/description vides en null', () => {
    api.createFormation.and.returnValue(ok(data[0], 'Formation ajoutée'));
    fill({ domaine: '   ', description: '' });
    component.save();

    expect(api.createFormation).toHaveBeenCalledWith(jasmine.objectContaining({
      diplome: 'Master', domaine: null, description: null, dateFin: '2026-06-30', enCours: false
    }));
    expect(component.success()).toBe('Formation ajoutée');
    expect(api.getFormations).toHaveBeenCalledTimes(2);
    jasmine.clock().tick(3000);
    expect(component.success()).toBe('');
  });

  it('création « en cours » : dateFin envoyée à null', () => {
    api.createFormation.and.returnValue(ok(data[1]));
    fill({ enCours: true, dateFin: '' });
    component.save();

    expect(api.createFormation).toHaveBeenCalledWith(jasmine.objectContaining({ enCours: true, dateFin: null }));
    jasmine.clock().tick(3000);
  });

  it('édition : appelle updateFormation avec l\'id', () => {
    api.updateFormation.and.returnValue(ok(data[0]));
    component.openEdit(data[0]);
    component.save();

    expect(api.updateFormation).toHaveBeenCalledWith(1, jasmine.objectContaining({ diplome: 'Licence' }));
    expect(api.createFormation).not.toHaveBeenCalled();
    jasmine.clock().tick(3000);
  });

  it('erreur backend à l\'enregistrement → message dans le formulaire', () => {
    api.createFormation.and.returnValue(throwError(() => ({ status: 400, error: { message: 'Date invalide' } })));
    fill();
    component.save();

    expect(component.formError()).toBe('Date invalide');
    expect(component.saving()).toBeFalse();
    expect(component.showForm).toBeTrue();
  });

  it('suppression : confirmDelete appelle l\'API puis recharge', () => {
    api.deleteFormation.and.returnValue(ok(undefined as void, 'Formation supprimée'));
    component.askDelete(data[0]);
    component.confirmDelete();

    expect(api.deleteFormation).toHaveBeenCalledWith(1);
    expect(component.toDelete).toBeNull();
    expect(api.getFormations).toHaveBeenCalledTimes(2);
    jasmine.clock().tick(3000);
  });

  it('suppression : sans sélection, rien ne se passe', () => {
    component.confirmDelete();
    expect(api.deleteFormation).not.toHaveBeenCalled();
  });

  it('suppression : erreur serveur injoignable', () => {
    api.deleteFormation.and.returnValue(throwError(() => ({ status: 0 })));
    component.askDelete(data[0]);
    component.confirmDelete();

    expect(component.pageError()).toContain('Serveur injoignable');
  });
});
