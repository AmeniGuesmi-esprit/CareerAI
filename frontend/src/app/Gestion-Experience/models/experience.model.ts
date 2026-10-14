/** Correspond à l'enum TypeContrat de l'experience-service */
export type TypeContrat =
  | 'CDI' | 'CDD' | 'STAGE' | 'ALTERNANCE'
  | 'FREELANCE' | 'TEMPS_PARTIEL' | 'BENEVOLAT' | 'AUTRE';

export const TYPES_CONTRAT: { value: TypeContrat; label: string }[] = [
  { value: 'CDI', label: 'CDI' },
  { value: 'CDD', label: 'CDD' },
  { value: 'STAGE', label: 'Stage' },
  { value: 'ALTERNANCE', label: 'Alternance' },
  { value: 'FREELANCE', label: 'Freelance' },
  { value: 'TEMPS_PARTIEL', label: 'Temps partiel' },
  { value: 'BENEVOLAT', label: 'Bénévolat' },
  { value: 'AUTRE', label: 'Autre' }
];

export function labelContrat(type: TypeContrat): string {
  return TYPES_CONTRAT.find(t => t.value === type)?.label ?? type;
}

/** Correspond à ExperienceResponse du experience-service */
export interface Experience {
  id: number;
  poste: string;
  entreprise: string;
  lieu: string | null;
  typeContrat: TypeContrat;
  dateDebut: string;        // 'yyyy-MM-dd'
  dateFin: string | null;   // null si enCours
  enCours: boolean;
  description: string | null;
  createdAt?: string;
  updatedAt?: string | null;
}

/** Correspond à ExperienceRequest du experience-service */
export interface ExperienceRequest {
  poste: string;
  entreprise: string;
  lieu: string | null;
  typeContrat: TypeContrat;
  dateDebut: string;
  dateFin: string | null;
  enCours: boolean;
  description: string | null;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}
