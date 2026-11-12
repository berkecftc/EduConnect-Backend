-- Backfill PROFILE_COMPLETE badge for users who already have a PROFILE_COMPLETED point history entry
INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT DISTINCT ph.user_id, 'PROFILE_COMPLETE', ph.created_at
FROM gamification_db.point_history ph
WHERE ph.action_type = 'PROFILE_COMPLETED'
ON CONFLICT (user_id, badge_type) DO NOTHING;