CREATE INDEX IF NOT EXISTS idx_user_reputation_total_points_user_id
    ON gamification_db.user_reputation (total_points DESC, user_id ASC);

