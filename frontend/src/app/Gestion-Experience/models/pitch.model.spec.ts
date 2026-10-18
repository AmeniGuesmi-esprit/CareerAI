import { PITCH_TABS } from './pitch.model';

describe('pitch.model', () => {
  it('définit les 3 onglets dans le bon ordre', () => {
    expect(PITCH_TABS.map(t => t.value)).toEqual(['courte', 'standard', 'enthousiaste']);
  });

  it('chaque onglet a un libellé et une icône', () => {
    PITCH_TABS.forEach(t => {
      expect(t.label).toBeTruthy();
      expect(t.icon.startsWith('ti-')).toBe(true);
    });
  });
});
