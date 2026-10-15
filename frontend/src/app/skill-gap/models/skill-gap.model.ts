/** Correspond à CvUploadResponse du skill-gap-service */
export interface CvUpload {
  id: number;
  userId: number;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  textLength: number;
  uploadedAt: string;
}

export type Priority = 'HIGH' | 'MEDIUM' | 'LOW';

/** Correspond à AnalysisResult.MissingSkill */
export interface MissingSkill {
  skill: string;
  priority: Priority;
  reason: string;
  howToImprove: string;
}

/** Correspond à AnalysisResult */
export interface AnalysisResult {
  matchScore: number;
  summary: string;
  strengths: string[];
  missingSkills: MissingSkill[];
  cvTips: string[];
}

/** Correspond à AnalysisResponse */
export interface SkillGapAnalysis {
  id: number;
  userId: number;
  cvId: number;
  cvFileName: string;
  jobTitle: string | null;
  jobDescription: string;
  matchScore: number;
  result: AnalysisResult;
  model: string;
  createdAt: string;
}
