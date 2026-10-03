CREATE TABLE gamification_db.leaderboard_preferences (
    user_id      UUID        PRIMARY KEY,
    visible      BOOLEAN     NOT NULL DEFAULT TRUE,
    display_mode VARCHAR(20) NOT NULL DEFAULT 'FULL_NAME' CHECK (display_mode IN ('FULL_NAME', 'INITIALS')),
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_point_history_created ON gamification_db.point_history (created_at, user_id);
