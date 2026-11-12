CREATE INDEX IF NOT EXISTS idx_point_history_user_action_created_at
    ON gamification_db.point_history (user_id, action_type, created_at);

