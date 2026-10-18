ALTER TABLE interview_sessions ADD COLUMN rag_trace MEDIUMTEXT NULL;
ALTER TABLE interview_feedbacks ADD COLUMN retrieved_sources TEXT NULL;
