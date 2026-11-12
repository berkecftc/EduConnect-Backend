ALTER TABLE post_db.posts DROP CONSTRAINT IF EXISTS posts_status_check;
ALTER TABLE post_db.posts
    ADD CONSTRAINT posts_status_check CHECK (status IN ('AWAITING_APPROVAL', 'PENDING', 'IN_REVIEW', 'PUBLISHED', 'REJECTED')),
    ADD COLUMN moderation_flag VARCHAR(255),
    ADD COLUMN submitted_at    TIMESTAMPTZ;

UPDATE post_db.posts SET submitted_at = COALESCE(updated_at, created_at) AT TIME ZONE 'Europe/Istanbul' WHERE status = 'PENDING';

ALTER TABLE post_db.comments DROP CONSTRAINT IF EXISTS comments_status_check;
ALTER TABLE post_db.comments ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE post_db.comments
    ADD CONSTRAINT comments_status_check CHECK (status IN ('PENDING', 'IN_REVIEW', 'PUBLISHED', 'REJECTED')),
    ADD COLUMN moderation_flag VARCHAR(255),
    ADD COLUMN moderation_note VARCHAR(1000),
    ADD COLUMN submitted_at    TIMESTAMPTZ;

CREATE TABLE post_db.moderation_records (
    id          UUID          PRIMARY KEY,
    target_type VARCHAR(10)   NOT NULL CHECK (target_type IN ('POST', 'COMMENT')),
    target_id   UUID          NOT NULL,
    post_id     UUID          NOT NULL REFERENCES post_db.posts (id) ON DELETE CASCADE,
    action      VARCHAR(20)   NOT NULL CHECK (action IN ('PUBLISHED', 'REJECTED', 'SENT_TO_REVIEW')),
    actor_type  VARCHAR(20)   NOT NULL CHECK (actor_type IN ('AUTOMATIC', 'MODERATOR')),
    actor_id    UUID,
    reason      VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_moderation_records_target ON post_db.moderation_records (target_type, target_id, created_at DESC);
CREATE INDEX idx_post_review_queue ON post_db.posts (status, submitted_at) WHERE status IN ('PENDING', 'IN_REVIEW');
CREATE INDEX idx_comment_review_queue ON post_db.comments (status, submitted_at) WHERE status IN ('PENDING', 'IN_REVIEW');
