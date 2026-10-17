/** Correspond à PitchRequest du experience-service */
export interface PitchRequest {
  /** Texte libre décrivant les expériences (champ attendu : "experiences"). */
  experiences: string;
}

/** Correspond à PitchResponse du experience-service */
export interface PitchResponse {
  courte: string;
  standard: string;      // ~80 mots, ~30 s à l'oral
  enthousiaste: string;
}

export type PitchTab = 'courte' | 'standard' | 'enthousiaste';

export const PITCH_TABS: { value: PitchTab; label: string; icon: string }[] = [
  { value: 'courte', label: 'Courte', icon: 'ti-bolt' },
  { value: 'standard', label: 'Standard', icon: 'ti-clock' },
  { value: 'enthousiaste', label: 'Enthousiaste', icon: 'ti-flame' }
];