/** Enveloppe commune des réponses du backend (ApiResponse) */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

/** Correspond à FormationResponse du competence-service */
export interface Formation {
  id: number;
  diplome: string;
  etablissement: string;
  domaine?: string | null;
  dateDebut: string;        // yyyy-MM-dd
  dateFin?: string | null;  // absente si enCours
  enCours: boolean;
  description?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** Correspond à FormationRequest */
export type FormationRequest = Omit<Formation, 'id' | 'createdAt' | 'updatedAt'>;

export type NiveauCompetence = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE' | 'EXPERT';
export type CategorieCompetence = 'TECHNIQUE' | 'SOFT_SKILL' | 'LANGUE' | 'OUTIL' | 'AUTRE';

export const NIVEAUX: { value: NiveauCompetence; label: string; rank: number }[] = [
  { value: 'DEBUTANT', label: 'Débutant', rank: 1 },
  { value: 'INTERMEDIAIRE', label: 'Intermédiaire', rank: 2 },
  { value: 'AVANCE', label: 'Avancé', rank: 3 },
  { value: 'EXPERT', label: 'Expert', rank: 4 }
];

export const CATEGORIES: { value: CategorieCompetence; label: string }[] = [
  { value: 'TECHNIQUE', label: 'Technique' },
  { value: 'SOFT_SKILL', label: 'Soft skill' },
  { value: 'LANGUE', label: 'Langue' },
  { value: 'OUTIL', label: 'Outil' },
  { value: 'AUTRE', label: 'Autre' }
];

/** Correspond à CompetenceResponse du competence-service */
export interface Competence {
  id: number;
  nom: string;
  niveau: NiveauCompetence;
  categorie: CategorieCompetence;
  createdAt?: string;
  updatedAt?: string;
}

/** Correspond à CompetenceRequest */
export type CompetenceRequest = Pick<Competence, 'nom' | 'niveau' | 'categorie'>;
