-- Flyway-first schema convergence:
-- 1) Backfill baseline schema for fresh environments (V1 is marker-only).
-- 2) Converge upload_items legacy statuses to the canonical set.

CREATE TABLE IF NOT EXISTS job_descriptions (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidates (
    id UUID PRIMARY KEY,
    source_file_name TEXT,
    file_hash VARCHAR(64),
    name VARCHAR(100),
    content_type TEXT,
    file_size_bytes BIGINT,
    parsed_char_count INTEGER,
    truncated BOOLEAN,
    recruitment_status VARCHAR(32),
    created_at TIMESTAMP,
    jd_id UUID
);

CREATE TABLE IF NOT EXISTS upload_batches (
    id UUID PRIMARY KEY,
    jd_id UUID,
    total_files INTEGER,
    processed_files INTEGER,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS upload_items (
    id UUID PRIMARY KEY,
    batch_id UUID,
    file_name TEXT,
    candidate_id UUID,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    storage_path TEXT,
    attempt INTEGER,
    queue_job_key VARCHAR(128),
    created_at TIMESTAMP,
    started_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    contact_value TEXT NOT NULL,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS educations (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    school TEXT NOT NULL,
    major TEXT,
    degree TEXT,
    graduation_year VARCHAR(10),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS experiences (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    company TEXT NOT NULL,
    position TEXT NOT NULL,
    description TEXT,
    start_date VARCHAR(20),
    end_date VARCHAR(20),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS honors (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS links (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    url TEXT NOT NULL,
    platform VARCHAR(50),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS candidate_snapshots (
    candidate_id UUID PRIMARY KEY,
    jd_id UUID,
    recruitment_status VARCHAR(32) NOT NULL,
    name VARCHAR(100),
    source_file_name TEXT,
    content_type TEXT,
    file_size_bytes BIGINT,
    parsed_char_count INTEGER,
    truncated BOOLEAN,
    candidate_created_at TIMESTAMP,
    emails_json TEXT,
    phones_json TEXT,
    educations_json TEXT,
    honors_json TEXT,
    links_json TEXT,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resume_chunks (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    chunk_type VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding TEXT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vector_ingest_tasks (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE job_descriptions
    ADD COLUMN IF NOT EXISTS title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS source_file_name TEXT,
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS content_type TEXT,
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS parsed_char_count INTEGER,
    ADD COLUMN IF NOT EXISTS truncated BOOLEAN,
    ADD COLUMN IF NOT EXISTS recruitment_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS jd_id UUID;

ALTER TABLE upload_batches
    ADD COLUMN IF NOT EXISTS jd_id UUID,
    ADD COLUMN IF NOT EXISTS total_files INTEGER,
    ADD COLUMN IF NOT EXISTS processed_files INTEGER,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE upload_items
    ADD COLUMN IF NOT EXISTS batch_id UUID,
    ADD COLUMN IF NOT EXISTS file_name TEXT,
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS storage_path TEXT,
    ADD COLUMN IF NOT EXISTS attempt INTEGER,
    ADD COLUMN IF NOT EXISTS queue_job_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE contacts
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS contact_value TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE educations
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS school TEXT,
    ADD COLUMN IF NOT EXISTS major TEXT,
    ADD COLUMN IF NOT EXISTS degree TEXT,
    ADD COLUMN IF NOT EXISTS graduation_year VARCHAR(10),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE experiences
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS company TEXT,
    ADD COLUMN IF NOT EXISTS position TEXT,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS start_date VARCHAR(20),
    ADD COLUMN IF NOT EXISTS end_date VARCHAR(20),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE honors
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE links
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS url TEXT,
    ADD COLUMN IF NOT EXISTS platform VARCHAR(50),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE candidate_snapshots
    ADD COLUMN IF NOT EXISTS jd_id UUID,
    ADD COLUMN IF NOT EXISTS recruitment_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_file_name TEXT,
    ADD COLUMN IF NOT EXISTS content_type TEXT,
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS parsed_char_count INTEGER,
    ADD COLUMN IF NOT EXISTS truncated BOOLEAN,
    ADD COLUMN IF NOT EXISTS candidate_created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS emails_json TEXT,
    ADD COLUMN IF NOT EXISTS phones_json TEXT,
    ADD COLUMN IF NOT EXISTS educations_json TEXT,
    ADD COLUMN IF NOT EXISTS honors_json TEXT,
    ADD COLUMN IF NOT EXISTS links_json TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

ALTER TABLE resume_chunks
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS chunk_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS embedding TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE vector_ingest_tasks
    ADD COLUMN IF NOT EXISTS candidate_id UUID,
    ADD COLUMN IF NOT EXISTS chunk_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS attempt INTEGER,
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_candidates_jd'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT fk_candidates_jd
                FOREIGN KEY (jd_id) REFERENCES job_descriptions (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_upload_batches_jd'
          AND conrelid = 'public.upload_batches'::regclass
    ) THEN
        ALTER TABLE upload_batches
            ADD CONSTRAINT fk_upload_batches_jd
                FOREIGN KEY (jd_id) REFERENCES job_descriptions (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_upload_items_batch'
          AND conrelid = 'public.upload_items'::regclass
    ) THEN
        ALTER TABLE upload_items
            ADD CONSTRAINT fk_upload_items_batch
                FOREIGN KEY (batch_id) REFERENCES upload_batches (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_contacts_candidate'
          AND conrelid = 'public.contacts'::regclass
    ) THEN
        ALTER TABLE contacts
            ADD CONSTRAINT fk_contacts_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_educations_candidate'
          AND conrelid = 'public.educations'::regclass
    ) THEN
        ALTER TABLE educations
            ADD CONSTRAINT fk_educations_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_experiences_candidate'
          AND conrelid = 'public.experiences'::regclass
    ) THEN
        ALTER TABLE experiences
            ADD CONSTRAINT fk_experiences_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_honors_candidate'
          AND conrelid = 'public.honors'::regclass
    ) THEN
        ALTER TABLE honors
            ADD CONSTRAINT fk_honors_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_links_candidate'
          AND conrelid = 'public.links'::regclass
    ) THEN
        ALTER TABLE links
            ADD CONSTRAINT fk_links_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_candidate_snapshots_candidate'
          AND conrelid = 'public.candidate_snapshots'::regclass
    ) THEN
        ALTER TABLE candidate_snapshots
            ADD CONSTRAINT fk_candidate_snapshots_candidate
                FOREIGN KEY (candidate_id) REFERENCES candidates (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_resume_chunks_candidate'
          AND conrelid = 'public.resume_chunks'::regclass
    ) THEN
        ALTER TABLE resume_chunks
            ADD CONSTRAINT fk_resume_chunks_candidate
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
        WHERE conname = 'uk_candidates_file_hash_jd'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT uk_candidates_file_hash_jd UNIQUE (file_hash, jd_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_upload_items_queue_job_key'
          AND conrelid = 'public.upload_items'::regclass
    ) THEN
        ALTER TABLE upload_items
            ADD CONSTRAINT uk_upload_items_queue_job_key UNIQUE (queue_job_key);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_candidates_jd_id
    ON candidates (jd_id);
CREATE INDEX IF NOT EXISTS idx_upload_batches_jd_id
    ON upload_batches (jd_id);
CREATE INDEX IF NOT EXISTS idx_upload_items_batch_id
    ON upload_items (batch_id);
CREATE INDEX IF NOT EXISTS idx_upload_items_status_updated_at
    ON upload_items (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_candidate_snapshots_jd
    ON candidate_snapshots (jd_id);
CREATE INDEX IF NOT EXISTS idx_candidate_id
    ON resume_chunks (candidate_id);
CREATE INDEX IF NOT EXISTS idx_chunk_type
    ON resume_chunks (chunk_type);
CREATE INDEX IF NOT EXISTS idx_vector_ingest_status_updated
    ON vector_ingest_tasks (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_vector_ingest_candidate
    ON vector_ingest_tasks (candidate_id);

UPDATE upload_items
SET status = 'QUEUED'
WHERE status IN ('PENDING', 'RETRYING');

UPDATE upload_items
SET status = 'DONE'
WHERE status = 'SUCCEEDED';

UPDATE upload_items
SET status = 'FAILED'
WHERE status IS NULL;

UPDATE upload_items
SET attempt = 0
WHERE attempt IS NULL;

DO
$$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT oid, conname
        FROM pg_constraint
        WHERE conrelid = 'public.upload_items'::regclass
          AND contype = 'c'
    LOOP
        IF pg_get_constraintdef(c.oid) ILIKE '%status%' THEN
            EXECUTE format('ALTER TABLE upload_items DROP CONSTRAINT %I', c.conname);
        END IF;
    END LOOP;

    ALTER TABLE upload_items
        ADD CONSTRAINT ck_upload_items_status
            CHECK (status IN ('QUEUED', 'PROCESSING', 'DONE', 'DUPLICATE', 'FAILED'));
END
$$;

DO
$$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT oid, conname
        FROM pg_constraint
        WHERE conrelid = 'public.upload_batches'::regclass
          AND contype = 'c'
    LOOP
        IF pg_get_constraintdef(c.oid) ILIKE '%status%' THEN
            EXECUTE format('ALTER TABLE upload_batches DROP CONSTRAINT %I', c.conname);
        END IF;
    END LOOP;

    ALTER TABLE upload_batches
        ADD CONSTRAINT ck_upload_batches_status
            CHECK (status IN ('PROCESSING', 'DONE'));
END
$$;

DO
$$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT oid, conname
        FROM pg_constraint
        WHERE conrelid = 'public.candidates'::regclass
          AND contype = 'c'
    LOOP
        IF pg_get_constraintdef(c.oid) ILIKE '%recruitment_status%' THEN
            EXECUTE format('ALTER TABLE candidates DROP CONSTRAINT %I', c.conname);
        END IF;
    END LOOP;

    ALTER TABLE candidates
        ADD CONSTRAINT ck_candidates_recruitment_status
            CHECK (recruitment_status IN ('TO_CONTACT', 'TO_INTERVIEW', 'REJECTED'));
END
$$;

DO
$$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT oid, conname
        FROM pg_constraint
        WHERE conrelid = 'public.vector_ingest_tasks'::regclass
          AND contype = 'c'
    LOOP
        IF pg_get_constraintdef(c.oid) ILIKE '%status%' THEN
            EXECUTE format('ALTER TABLE vector_ingest_tasks DROP CONSTRAINT %I', c.conname);
        END IF;
    END LOOP;

    ALTER TABLE vector_ingest_tasks
        ADD CONSTRAINT ck_vector_ingest_tasks_status
            CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'));
END
$$;
