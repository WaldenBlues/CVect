ALTER TABLE job_descriptions
    ADD COLUMN IF NOT EXISTS embedding TEXT,
    ADD COLUMN IF NOT EXISTS embedding_updated_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS candidate_match_scores (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    jd_id UUID NOT NULL,
    overall_score REAL NOT NULL,
    experience_score REAL NOT NULL,
    skill_score REAL NOT NULL,
    scored_at TIMESTAMP NOT NULL
);

ALTER TABLE candidate_match_scores
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS jd_id UUID,
    ADD COLUMN IF NOT EXISTS overall_score REAL,
    ADD COLUMN IF NOT EXISTS experience_score REAL,
    ADD COLUMN IF NOT EXISTS skill_score REAL,
    ADD COLUMN IF NOT EXISTS scored_at TIMESTAMP;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_candidate_match_scores_candidate_jd'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores
            ADD CONSTRAINT uk_candidate_match_scores_candidate_jd UNIQUE (candidate_id, jd_id);
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_candidate_match_scores_candidate'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores
            ADD CONSTRAINT fk_candidate_match_scores_candidate
            FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;
END
$$;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_candidate_match_scores_jd'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores
            ADD CONSTRAINT fk_candidate_match_scores_jd
            FOREIGN KEY (jd_id) REFERENCES job_descriptions (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_candidate_match_scores_candidate_id
    ON candidate_match_scores (candidate_id);

CREATE INDEX IF NOT EXISTS idx_candidate_match_scores_jd_id
    ON candidate_match_scores (jd_id);
