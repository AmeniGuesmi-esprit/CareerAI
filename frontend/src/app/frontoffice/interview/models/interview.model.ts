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
