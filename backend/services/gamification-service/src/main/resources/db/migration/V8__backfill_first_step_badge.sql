-- Backfill FIRST_STEP badge for all users who already have at least 1 point
INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'FIRST_STEP', NOW()
FROM gamification_db.user_reputation
WHERE total_points >= 1
ON CONFLICT (user_id, badge_type) DO NOTHING;