INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'POINTS_MASTER', NOW()
FROM gamification_db.user_reputation
WHERE total_points >= 1000
ON CONFLICT (user_id, badge_type) DO NOTHING;

INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'POINTS_EXPLORER', NOW()
FROM gamification_db.user_reputation
WHERE total_points >= 250
ON CONFLICT (user_id, badge_type) DO NOTHING;

INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'STREAK_LEGEND', NOW()
FROM gamification_db.user_reputation
WHERE highest_streak >= 30
ON CONFLICT (user_id, badge_type) DO NOTHING;

INSERT INTO gamification_db.user_badges (user_id, badge_type, earned_at)
SELECT user_id, 'WEEK_WARRIOR', NOW()
FROM gamification_db.user_reputation
WHERE highest_streak >= 7
ON CONFLICT (user_id, badge_type) DO NOTHING;

