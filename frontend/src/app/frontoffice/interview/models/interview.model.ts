export type SessionStatus = 'GENERATING' | 'READY' | 'FAILED';
export type FeedbackStatus = 'PENDING' | 'READY' | 'FAILED';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CvSummary {
  id: number;
  label: string;
  fileName: string;
  textLength: number;
  createdAt: string;
}

export interface SessionSummary {
  id: number;
  jobTitle: string;
  cvLabel: string;
  status: SessionStatus;
  questionCount: number;
  answeredCount: number;
  track: string;
  level: string;
  createdAt: string;
}

export interface SourceRef {
  citationId: string;
  title: string;
  docType: 'question_bank' | 'guide' | 'rubric';
  niveau: string;
  section: string;
}

export interface FeedbackView {
  status: FeedbackStatus;
  errorCode: string | null;
  score: number | null;
  verdict: string | null;
  strengths: string[];
  gaps: string[];
  improvedAnswer: string | null;
  star: Record<string, string> | null;
  citedSourceIds: string[];
  model: string | null;
  promptVersion: string | null;
  updatedAt: string;
  sources: SourceRef[];
}

export interface RagHit {
  citationId: string;
  title: string;
  track: string;
  docType: 'question_bank' | 'guide' | 'rubric';
  niveau: string;
  section: string;
  score: number;
  denseRank: number | null;
  bm25Rank: number | null;
}

export interface RagQuestion {
  questionId: number;
  position: number;
  question: string;
  usedCitationIds: string[];
}

export interface RagView {
  track: string;
  level: string;
  mode: 'hybrid' | 'bm25';
  matchedSkills: string[];
  gapSkills: string[];
  queries: string[];
  retrieved: RagHit[];
  promptVersion: string;
  model: string;
  seed: number | null;
  manifestHash: string | null;
  questions: RagQuestion[];
}

export interface KnowledgeDoc {
  docId: string;
  docType: string;
  track: string;
  items: number;
  sections: string[];
}

export interface KnowledgeOverview {
  mode: 'hybrid' | 'bm25';
  items: number;
  itemsByTrack: Record<string, number>;
  documents: KnowledgeDoc[];
  manifest: {
    corpusHash: string;
    chatModel: string;
    embedModel: string | null;
    dims: number | null;
    chunkCount: number;
    builtAt: string;
  } | null;
}

export interface KnowledgeSearchRequest {
  query: string;
  track?: string;
  docType?: string;
  k?: number;
}

export interface KnowledgeSearchHit {
  citationId: string;
  title: string;
  track: string;
  docType: 'question_bank' | 'guide' | 'rubric';
  niveau: string;
  section: string;
  excerpt: string;
  score: number;
  denseRank: number | null;
  bm25Rank: number | null;
}

export interface KnowledgeSearchResult {
  mode: 'hybrid' | 'bm25';
  track: string;
  query: string;
  hits: KnowledgeSearchHit[];
}

export interface AnswerView {
  id: number;
  content: string;
  updatedAt: string;
  feedback: FeedbackView | null;
}

export interface QuestionView {
  id: number;
  position: number;
  category: 'technique' | 'comportementale';
  difficulty: 'junior' | 'confirme' | 'senior';
  question: string;
  intent: string;
  expectedPoints: string[];
  answer: AnswerView | null;
}

export interface SessionDetail {
  id: number;
  jobTitle: string;
  jobDescription: string;
  cvLabel: string;
  status: SessionStatus;
  errorCode: string | null;
  track: string;
  level: string;
  matchedSkills: string[];
  gapSkills: string[];
  compatAnalysisId: number | null;
  questionCount: number;
  createdAt: string;
  updatedAt: string;
  questions: QuestionView[];
}

export interface CreateSessionRequest {
  cvId: number;
  jobTitle: string;
  jobDescription: string;
  questionCount?: number;
  analysisId?: number;
}
