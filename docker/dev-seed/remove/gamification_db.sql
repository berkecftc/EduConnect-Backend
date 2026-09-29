DELETE FROM gamification_db.point_history WHERE user_id::text LIKE '5eed%';
DELETE FROM gamification_db.user_badges WHERE user_id::text LIKE '5eed%';
DELETE FROM gamification_db.user_reputation WHERE user_id::text LIKE '5eed%';
