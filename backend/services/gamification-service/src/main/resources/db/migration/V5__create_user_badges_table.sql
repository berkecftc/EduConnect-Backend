CREATE TABLE gamification_db.user_badges (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    badge_type VARCHAR(50) NOT NULL,
    earned_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_badge UNIQUE (user_id, badge_type)
);
CREATE INDEX idx_user_badges_user_id ON gamification_db.user_badges (user_id);
