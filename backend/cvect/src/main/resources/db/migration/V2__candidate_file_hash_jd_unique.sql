DO
$$
BEGIN
    IF to_regclass('public.candidates') IS NULL THEN
        RAISE NOTICE 'Skip candidate unique migration because table public.candidates does not exist yet';
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_candidates_file_hash'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            DROP CONSTRAINT uk_candidates_file_hash;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_candidates_file_hash_jd'
          AND conrelid = 'public.candidates'::regclass
    ) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT uk_candidates_file_hash_jd UNIQUE (file_hash, jd_id);
    END IF;
END
$$;
