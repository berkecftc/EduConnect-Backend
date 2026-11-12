ALTER TABLE gamification_db.point_history ADD COLUMN content_id UUID;
CREATE INDEX idx_point_history_content ON gamification_db.point_history (user_id, content_id) WHERE content_id IS NOT NULL;

UPDATE gamification_db.user_reputation r
SET total_points = GREATEST(0, r.total_points - s.points)
FROM (SELECT user_id, SUM(points_earned) AS points
      FROM gamification_db.point_history
      WHERE action_type = 'DAILY_LOGIN'
      GROUP BY user_id) s
WHERE s.user_id = r.user_id;

DELETE FROM gamification_db.point_history WHERE action_type = 'DAILY_LOGIN';

ALTER TABLE gamification_db.user_reputation RENAME COLUMN last_login_date TO last_contribution_week;
UPDATE gamification_db.user_reputation SET current_streak = 0, highest_streak = 0, last_contribution_week = NULL;
