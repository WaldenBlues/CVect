ALTER TABLE job_descriptions
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID;

UPDATE job_descriptions jd
SET created_by_user_id = u.id
FROM auth_users u
WHERE jd.created_by_user_id IS NULL
  AND u.tenant_id = jd.tenant_id
  AND u.username = 'demo';

DO
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_job_descriptions_created_by_user'
          AND conrelid = 'public.job_descriptions'::regclass
    ) THEN
        ALTER TABLE job_descriptions
            ADD CONSTRAINT fk_job_descriptions_created_by_user
            FOREIGN KEY (created_by_user_id) REFERENCES auth_users (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_job_descriptions_tenant_creator_created
    ON job_descriptions (tenant_id, created_by_user_id, created_at DESC);
