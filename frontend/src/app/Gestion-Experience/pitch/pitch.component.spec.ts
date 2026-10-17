import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { NEVER, of, throwError } from 'rxjs';
import { PitchComponent } from './pitch.component';
import { PitchService } from '../services/pitch.service';
import { PitchRequest, PitchResponse } from '../models/pitch.model';

const PITCH: PitchResponse = { courte: 'Court', standard: 'un deux  trois', enthousiaste: '' };
const ok = <T>(data: T) => of({ success: true, message: '', data });

/** Crée le composant sans TestBed avec un faux service. */
const setup = (generate: (r: PitchRequest) => unknown) =>
  new PitchComponent({ generate } as unknown as PitchService);

describe('PitchComponent', () => {
  describe('generate()', () => {
    it('refuse un texte vide sans appeler le service', () => {
      let called = false;
      const cmp = setup(() => { called = true; return NEVER; });
      cmp.experiences.set('   ');
      cmp.generate();
      expect(called).toBe(false);
      expect(cmp.error()).toContain('Décrivez vos expériences');
    });

    it('envoie le texte nettoyé et affiche le pitch', () => {
      let sent: PitchRequest | undefined;
      const cmp = setup(r => { sent = r; return ok(PITCH); });
      cmp.experiences.set('  Stage chez ACME  ');
      cmp.activeTab.set('courte');
      cmp.generate();
      expect(sent).toEqual({ experiences: 'Stage chez ACME' });
      expect(cmp.pitch()).toEqual(PITCH);
      expect(cmp.activeTab()).toBe('standard');
      expect(cmp.loading()).toBe(false);
    });

    it('passe en chargement, vide le pitch précédent et ignore un 2e clic', () => {
      let calls = 0;
      const cmp = setup(() => { calls++; return NEVER; });
      cmp.experiences.set('Stage');
      cmp.pitch.set(PITCH);
      cmp.generate();
      cmp.generate();
      expect(calls).toBe(1);
      expect(cmp.loading()).toBe(true);
      expect(cmp.pitch()).toBeNull();
    });

    it('met pitch à null si la réponse n\'a pas de data', () => {
      const cmp = setup(() => ok(undefined));
      cmp.experiences.set('Stage');
      cmp.generate();
      expect(cmp.pitch()).toBeNull();
    });
  });

  describe('messages d\'erreur', () => {
    const cases: [string, HttpErrorResponse, string][] = [
      ['serveur injoignable (0)', new HttpErrorResponse({ status: 0 }),
        'Impossible de joindre le serveur. Vérifiez que les services sont démarrés.'],
      ['429 avec Retry-After', new HttpErrorResponse({ status: 429, error: { detail: 'Quota atteint.' }, headers: new HttpHeaders({ 'Retry-After': '30' }) }),
        'Quota atteint. Réessayez dans 30 s.'],
      ['429 sans détail', new HttpErrorResponse({ status: 429 }), 'Trop de requêtes.'],
      ['détail du serveur', new HttpErrorResponse({ status: 500, error: { detail: 'Boom' } }), 'Boom'],
      ['message du serveur', new HttpErrorResponse({ status: 500, error: { message: 'Oups' } }), 'Oups'],
      ['401', new HttpErrorResponse({ status: 401 }), 'Session expirée. Reconnectez-vous.'],
      ['403', new HttpErrorResponse({ status: 403 }), 'Session expirée. Reconnectez-vous.'],
      ['400', new HttpErrorResponse({ status: 400 }), 'Requête invalide.'],
      ['503', new HttpErrorResponse({ status: 503 }), "Le service d'IA est temporairement indisponible."],
      ['autre erreur', new HttpErrorResponse({ status: 500 }), 'La génération du pitch a échoué. Veuillez réessayer.']
    ];

    cases.forEach(([name, err, message]) =>
      it(name, () => {
        const cmp = setup(() => throwError(() => err));
        cmp.experiences.set('Stage');
        cmp.generate();
        expect(cmp.error()).toBe(message);
        expect(cmp.loading()).toBe(false);
      })
    );
  });

  describe('aides', () => {
    it('selectTab change l\'onglet actif', () => {
      const cmp = setup(() => NEVER);
      cmp.selectTab('courte');
      expect(cmp.activeTab()).toBe('courte');
    });

    it('textOf renvoie \'\' sans pitch, sinon le texte de l\'onglet', () => {
      const cmp = setup(() => NEVER);
      expect(cmp.textOf('courte')).toBe('');
      cmp.pitch.set(PITCH);
      expect(cmp.textOf('courte')).toBe('Court');
    });

    it('wordCount compte les mots (espaces multiples ignorés)', () => {
      const cmp = setup(() => NEVER);
      cmp.pitch.set(PITCH);
      expect(cmp.wordCount('standard')).toBe(3);
      expect(cmp.wordCount('enthousiaste')).toBe(0);
    });

    it('reset vide le pitch et l\'erreur', () => {
      const cmp = setup(() => NEVER);
      cmp.pitch.set(PITCH);
      cmp.error.set('x');
      cmp.reset();
      expect(cmp.pitch()).toBeNull();
      expect(cmp.error()).toBe('');
    });
  });

  describe('copy()', () => {
    const originalExec = document.execCommand;
    const stubClipboard = (writeText: (t: string) => Promise<void>) =>
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

    afterEach(() => { document.execCommand = originalExec; });

    it('ne fait rien si le texte est vide', async () => {
      let written = '';
      stubClipboard(async t => { written = t; });
      const cmp = setup(() => NEVER);
      await cmp.copy('courte');
      expect(written).toBe('');
      expect(cmp.copied()).toBe('');
    });

    it('copie via l\'API Clipboard et marque l\'onglet comme copié', async () => {
      let written = '';
      stubClipboard(async t => { written = t; });
      const cmp = setup(() => NEVER);
      cmp.pitch.set(PITCH);
      await cmp.copy('courte');
      expect(written).toBe('Court');
      expect(cmp.copied()).toBe('courte');
    });

    it('se replie sur execCommand si l\'API Clipboard échoue', async () => {
      stubClipboard(() => Promise.reject(new Error('refusé')));
      let command = '';
      document.execCommand = (c: string) => { command = c; return true; };
      const cmp = setup(() => NEVER);
      cmp.pitch.set(PITCH);
      await cmp.copy('courte');
      expect(command).toBe('copy');
      expect(cmp.copied()).toBe('courte');
      expect(document.querySelector('textarea')).toBeNull(); // textarea temporaire nettoyé
    });
  });
});
