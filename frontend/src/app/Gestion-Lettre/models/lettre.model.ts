/** Correspond à LettreResponse du lettre-service */
export interface Lettre {
  id: number;
  userEmail: string;
  nomEntreprise: string;
  poste?: string | null;
  cv: string;
  contenu: string;
  createdAt: string;
  updatedAt?: string | null;
}

/** Correspond à LettreRequest du lettre-service (formulaire de création / modification) */
export interface LettreRequest {
  nomEntreprise: string;
  poste?: string;
  cv: string;
  contenu: string;
}

// ── Assistant IA ──

/** Fournisseur IA (GET /api/lettres/ai/providers) */
export interface AiProvider {
  name: string;
  model: string;
  configured: boolean;
  preferred: boolean;
}

export interface AiGenerateRequest {
  nomEntreprise: string;
  poste?: string;
  offre: string;
  cv: string;
  ton: string;
  langue: string;
  provider?: string;
}

export interface AiImproveRequest {
  contenu: string;
  offre?: string;
  instruction?: string;
  provider?: string;
}

/** Résultat d'une génération ou d'une amélioration (score et suggestions : amélioration seulement). */
export interface AiLetterResult {
  contenu: string;
  score?: number | null;
  suggestions?: string[] | null;
  provider: string;
  model: string;
}