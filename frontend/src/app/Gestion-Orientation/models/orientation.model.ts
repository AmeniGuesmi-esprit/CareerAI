/** Correspond à AdviceResponse du competence-service */
export interface Advice {
  reponse: string;
  statut: 'VERIFIEE' | 'A_VERIFIER' | 'NON_VERIFIEE';
  avertissement?: string | null;
}
