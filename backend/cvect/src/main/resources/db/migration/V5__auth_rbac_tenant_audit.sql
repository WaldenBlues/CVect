CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

INSERT INTO tenants (id, name, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Tenant', 'ACTIVE', current_timestamp)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS auth_users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_auth_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_auth_users_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS auth_roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_auth_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_auth_roles_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS auth_permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_auth_permissions_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS auth_user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_auth_user_roles_user FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_user_roles_role FOREIGN KEY (role_id) REFERENCES auth_roles (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_auth_role_permissions_role FOREIGN KEY (role_id) REFERENCES auth_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES auth_permissions (id) ON DELETE CASCADE
);

ALTER TABLE job_descriptions
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE candidate_snapshots
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE upload_batches
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE upload_items
    ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE candidate_match_scores
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE job_descriptions
SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id IS NULL;

UPDATE candidates
SET tenant_id = COALESCE(
    (SELECT jd.tenant_id FROM job_descriptions jd WHERE jd.id = candidates.jd_id),
    '00000000-0000-0000-0000-000000000001'
)
WHERE tenant_id IS NULL;

UPDATE candidate_snapshots
SET tenant_id = COALESCE(
    (SELECT c.tenant_id FROM candidates c WHERE c.id = candidate_snapshots.candidate_id),
    '00000000-0000-0000-0000-000000000001'
)
WHERE tenant_id IS NULL;

UPDATE upload_batches
SET tenant_id = COALESCE(
    (SELECT jd.tenant_id FROM job_descriptions jd WHERE jd.id = upload_batches.jd_id),
    '00000000-0000-0000-0000-000000000001'
)
WHERE tenant_id IS NULL;

UPDATE upload_items
SET tenant_id = COALESCE(
    (SELECT b.tenant_id FROM upload_batches b WHERE b.id = upload_items.batch_id),
    '00000000-0000-0000-0000-000000000001'
)
WHERE tenant_id IS NULL;

UPDATE candidate_match_scores
SET tenant_id = COALESCE(
    (SELECT jd.tenant_id FROM job_descriptions jd WHERE jd.id = candidate_match_scores.jd_id),
    (SELECT c.tenant_id FROM candidates c WHERE c.id = candidate_match_scores.candidate_id),
    '00000000-0000-0000-0000-000000000001'
)
WHERE tenant_id IS NULL;

ALTER TABLE job_descriptions
    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE candidates
    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE candidate_snapshots
    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE upload_batches
    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE upload_items
    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE candidate_match_scores
    ALTER COLUMN tenant_id SET NOT NULL;

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_job_descriptions_tenant'
          AND conrelid = 'public.job_descriptions'::regclass
    ) THEN
        ALTER TABLE job_descriptions
            ADD CONSTRAINT fk_job_descriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_candidates_tenant'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT fk_candidates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_candidate_snapshots_tenant'
          AND conrelid = 'public.candidate_snapshots'::regclass
    ) THEN
        ALTER TABLE candidate_snapshots
            ADD CONSTRAINT fk_candidate_snapshots_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_upload_batches_tenant'
          AND conrelid = 'public.upload_batches'::regclass
    ) THEN
        ALTER TABLE upload_batches
            ADD CONSTRAINT fk_upload_batches_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_upload_items_tenant'
          AND conrelid = 'public.upload_items'::regclass
    ) THEN
        ALTER TABLE upload_items
            ADD CONSTRAINT fk_upload_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_candidate_match_scores_tenant'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores
            ADD CONSTRAINT fk_candidate_match_scores_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_candidates_file_hash_jd'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates DROP CONSTRAINT uk_candidates_file_hash_jd;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_candidates_tenant_file_hash_jd'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT uk_candidates_tenant_file_hash_jd UNIQUE (tenant_id, file_hash, jd_id);
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_candidate_match_scores_candidate_jd'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores DROP CONSTRAINT uk_candidate_match_scores_candidate_jd;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_candidate_match_scores_tenant_candidate_jd'
          AND conrelid = 'public.candidate_match_scores'::regclass
    ) THEN
        ALTER TABLE candidate_match_scores
            ADD CONSTRAINT uk_candidate_match_scores_tenant_candidate_jd UNIQUE (tenant_id, candidate_id, jd_id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_job_descriptions_tenant_id
    ON job_descriptions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_candidates_tenant_jd_created
    ON candidates (tenant_id, jd_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_snapshots_tenant_jd
    ON candidate_snapshots (tenant_id, jd_id);
CREATE INDEX IF NOT EXISTS idx_upload_batches_tenant_jd
    ON upload_batches (tenant_id, jd_id);
CREATE INDEX IF NOT EXISTS idx_upload_items_tenant_batch
    ON upload_items (tenant_id, batch_id);
CREATE INDEX IF NOT EXISTS idx_candidate_match_scores_tenant_jd
    ON candidate_match_scores (tenant_id, jd_id);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    user_id UUID,
    username VARCHAR(100),
    action VARCHAR(120) NOT NULL,
    target VARCHAR(120),
    target_id VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    http_method VARCHAR(16),
    request_path TEXT,
    request_id VARCHAR(100),
    client_ip VARCHAR(100),
    args_summary TEXT,
    result_summary TEXT,
    error_type VARCHAR(200),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_created
    ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_created
    ON audit_logs (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created
    ON audit_logs (action, created_at DESC);
