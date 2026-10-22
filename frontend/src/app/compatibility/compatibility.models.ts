/** Élément de la liste (historique) — correspond à AnalysisListItem du compatibility-service */
export interface AnalysisListItem {
  id: number;
  cvFileName: string;
  jobTitle: string;
  score: number;
  createdAt: string;
}

/** Mesures de l'appel IA — correspond à AiMetrics du compatibility-service */
export interface AiMetrics {
  model: string;
  llmCalls: number;
  latencyMs: number;
  promptTokens: number;
  completionTokens: number;
  /** true : résultat resservi depuis le cache, sans appel au modèle */
  cached: boolean;
}

/** Détail d'une analyse — correspond à AnalysisResponse du compatibility-service */
export interface AnalysisResponse extends AnalysisListItem {
  summary: string;
  strengths: string[];
  missingSkills: string[];
  suggestions: string[];
    verifiedSkills: string[];
  jobDescription: string;
  /** null pour les analyses antérieures à la mesure */
  metrics: AiMetrics | null;
}

/** "41,3 s", "2 min 05 s" ou "12 ms" */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  const seconds = ms / 1000;
  if (seconds < 60) return `${seconds.toFixed(1).replace('.', ',')} s`;
  const min = Math.floor(seconds / 60);
  return `${min} min ${String(Math.round(seconds % 60)).padStart(2, '0')} s`;
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