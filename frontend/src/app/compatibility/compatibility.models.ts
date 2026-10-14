/** Élément de la liste (historique) — correspond à AnalysisListItem du compatibility-service */
export interface AnalysisListItem {
  id: number;
  cvFileName: string;
  jobTitle: string;
  score: number;
  createdAt: string;
}

/** Détail d'une analyse — correspond à AnalysisResponse du compatibility-service */
export interface AnalysisResponse extends AnalysisListItem {
  summary: string;
  strengths: string[];
  missingSkills: string[];
  suggestions: string[];
    verifiedSkills: string[];
  jobDescription: string;
}

/** Page Spring Data (seuls les champs utilisés sont déclarés) */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type ScoreLevel = 'low' | 'medium' | 'high';

export function scoreLevel(score: number): ScoreLevel {
  if (score >= 70) return 'high';
  if (score >= 40) return 'medium';
  return 'low';
}

export function scoreLabel(score: number): string {
  switch (scoreLevel(score)) {
    case 'high': return 'Très bonne compatibilité';
    case 'medium': return 'Compatibilité moyenne';
    default: return 'Compatibilité faible';
  }
}