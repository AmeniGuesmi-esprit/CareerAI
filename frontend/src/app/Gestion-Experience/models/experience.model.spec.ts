import { TYPES_CONTRAT, labelContrat } from './experience.model';

describe('experience.model', () => {
  it('expose 8 types de contrat uniques', () => {
    const values = TYPES_CONTRAT.map(t => t.value);
    expect(values.length).toBe(8);
    expect(new Set(values).size).toBe(8);
  });

  it('labelContrat renvoie le libellé lisible', () => {
    expect(labelContrat('CDI')).toBe('CDI');
    expect(labelContrat('STAGE')).toBe('Stage');
    expect(labelContrat('TEMPS_PARTIEL')).toBe('Temps partiel');
    expect(labelContrat('BENEVOLAT')).toBe('Bénévolat');
  });

  it('labelContrat renvoie la valeur brute si le type est inconnu', () => {
    expect(labelContrat('INCONNU' as never)).toBe('INCONNU');
  });
});
