-- Backfill FORTNIGHT_WARRIOR badge for users who already have 14+ day streak
INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'FORTNIGHT_WARRIOR', NOW()
FROM gamification_db.user_reputation
WHERE highest_streak >= 14
ON CONFLICT (user_id, badge_type) DO NOTHING;

-- Backfill STREAK_LEGEND for users with 28+ day streak (threshold lowered from 30 to 28)
INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'STREAK_LEGEND', NOW()
FROM gamification_db.user_reputation
WHERE highest_streak >= 28
ON CONFLICT (user_id, badge_type) DO NOTHING;