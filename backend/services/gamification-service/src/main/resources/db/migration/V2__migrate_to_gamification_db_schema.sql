CREATE SCHEMA IF NOT EXISTS gamification_db;

-- Existing installs may already have tables under gamification_schema.
-- Move them to gamification_db only when target tables are not present.
DO $$
BEGIN
    IF to_regclass('gamification_schema.user_reputation') IS NOT NULL
       AND to_regclass('gamification_db.user_reputation') IS NULL THEN
        EXECUTE 'ALTER TABLE gamification_schema.user_reputation SET SCHEMA gamification_db';
    END IF;

    IF to_regclass('gamification_schema.point_history') IS NOT NULL
       AND to_regclass('gamification_db.point_history') IS NULL THEN
        EXECUTE 'ALTER TABLE gamification_schema.point_history SET SCHEMA gamification_db';
    END IF;
END $$;

-- Fresh installs should have the canonical tables in gamification_db.
CREATE TABLE IF NOT EXISTS gamification_db.user_reputation (
    user_id UUID PRIMARY KEY,
    total_points INTEGER NOT NULL DEFAULT 0,
    current_streak INTEGER NOT NULL DEFAULT 0,
    highest_streak INTEGER NOT NULL DEFAULT 0,
    last_login_date DATE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gamification_db.point_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(150) NOT NULL,
    points_earned INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_point_history_user_action_reference
        UNIQUE (user_id, action_type, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_point_history_user_id
    ON gamification_db.point_history (user_id);

CREATE INDEX IF NOT EXISTS idx_point_history_created_at
    ON gamification_db.point_history (created_at);

