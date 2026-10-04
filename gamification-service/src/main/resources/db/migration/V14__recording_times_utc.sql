ALTER TABLE gamification_db.point_history
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE gamification_db.user_badges
    ALTER COLUMN earned_at TYPE timestamptz USING earned_at AT TIME ZONE 'Europe/Istanbul';
