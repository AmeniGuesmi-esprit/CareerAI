CREATE TABLE cv_documents (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    label           VARCHAR(150) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    anonymized_text MEDIUMTEXT   NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    INDEX idx_cv_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE interview_sessions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    cv_id              BIGINT       NULL,
    cv_label           VARCHAR(150) NOT NULL,
    job_title          VARCHAR(150) NOT NULL,
    job_description    TEXT         NOT NULL,
    cv_text            MEDIUMTEXT   NOT NULL,
    compat_analysis_id BIGINT       NULL,
    compat_context     TEXT         NULL,
    question_count     INT          NOT NULL,
    track              VARCHAR(40)  NOT NULL,
    level              VARCHAR(20)  NOT NULL,
    matched_skills     TEXT         NULL,
    gap_skills         TEXT         NULL,
    status             VARCHAR(20)  NOT NULL,
    error_code         VARCHAR(60)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    INDEX idx_session_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE interview_questions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    position        INT          NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    question        TEXT         NOT NULL,
    intent          TEXT         NOT NULL,
    expected_points TEXT         NOT NULL,
    source_ids      TEXT         NOT NULL,
    prompt_version  VARCHAR(40)  NULL,
    model           VARCHAR(100) NULL,
    seed            INT          NULL,
    rag_manifest_hash CHAR(64)   NULL,
    CONSTRAINT fk_question_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
    UNIQUE KEY uq_question_position (session_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE interview_answers (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT      NOT NULL,
    content     TEXT        NOT NULL,
    answer_hash CHAR(64)    NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES interview_questions (id) ON DELETE CASCADE,
    UNIQUE KEY uq_answer_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE interview_feedbacks (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    answer_id        BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL,
    error_code       VARCHAR(60) NULL,
    score            INT         NULL,
    verdict          TEXT        NULL,
    strengths        TEXT        NULL,
    gaps             TEXT        NULL,
    improved_answer  TEXT        NULL,
    star             TEXT        NULL,
    cited_source_ids TEXT        NULL,
    answer_hash      CHAR(64)    NOT NULL,
    prompt_version   VARCHAR(40) NULL,
    model            VARCHAR(100) NULL,
    seed             INT         NULL,
    rag_manifest_hash CHAR(64)   NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT fk_feedback_answer FOREIGN KEY (answer_id) REFERENCES interview_answers (id) ON DELETE CASCADE,
    UNIQUE KEY uq_feedback_answer (answer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rag_chunks (
    chunk_id       CHAR(64)     PRIMARY KEY,
    doc_id         VARCHAR(100) NOT NULL,
    citation_id    VARCHAR(255) NOT NULL,
    track          VARCHAR(40)  NOT NULL,
    doc_type       VARCHAR(30)  NOT NULL,
    niveau         VARCHAR(20)  NULL,
    section        VARCHAR(100) NULL,
    title          VARCHAR(255) NOT NULL,
    tags           TEXT         NULL,
    body           MEDIUMTEXT   NOT NULL,
    index_text     MEDIUMTEXT   NOT NULL,
    content_hash   CHAR(64)     NOT NULL,
    embed_model    VARCHAR(100) NULL,
    embedding      MEDIUMBLOB   NULL,
    INDEX idx_chunk_doc (doc_id),
    INDEX idx_chunk_citation (citation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rag_index_manifest (
    id            INT          PRIMARY KEY,
    corpus_hash   CHAR(64)     NOT NULL,
    chat_model    VARCHAR(100) NOT NULL,
    embed_model   VARCHAR(100) NULL,
    embed_digest  VARCHAR(100) NULL,
    dims          INT          NULL,
    chunk_count   INT          NOT NULL,
    built_at      DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
