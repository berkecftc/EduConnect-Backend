CREATE SCHEMA IF NOT EXISTS gamification_schema;

CREATE TABLE IF NOT EXISTS gamification_schema.user_reputation (
    user_id UUID PRIMARY KEY,
    total_points INTEGER NOT NULL DEFAULT 0,
    current_streak INTEGER NOT NULL DEFAULT 0,
    highest_streak INTEGER NOT NULL DEFAULT 0,
    last_login_date DATE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gamification_schema.point_history (
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
    ON gamification_schema.point_history (user_id);

CREATE INDEX IF NOT EXISTS idx_point_history_created_at
    ON gamification_schema.point_history (created_at);

