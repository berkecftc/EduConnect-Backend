ALTER TABLE post_db.posts DROP CONSTRAINT IF EXISTS posts_status_check;
ALTER TABLE post_db.posts
    ADD CONSTRAINT posts_status_check CHECK (status IN ('AWAITING_APPROVAL', 'PENDING', 'IN_REVIEW', 'PUBLISHED', 'REJECTED', 'HIDDEN', 'REMOVED'));

ALTER TABLE post_db.comments DROP CONSTRAINT IF EXISTS comments_status_check;
ALTER TABLE post_db.comments
    ADD CONSTRAINT comments_status_check CHECK (status IN ('PENDING', 'IN_REVIEW', 'PUBLISHED', 'REJECTED', 'HIDDEN', 'REMOVED'));

ALTER TABLE post_db.moderation_records DROP CONSTRAINT IF EXISTS moderation_records_action_check;
ALTER TABLE post_db.moderation_records DROP CONSTRAINT IF EXISTS moderation_records_actor_type_check;
ALTER TABLE post_db.moderation_records
    ADD CONSTRAINT moderation_records_action_check
        CHECK (action IN ('PUBLISHED', 'REJECTED', 'SENT_TO_REVIEW', 'HIDDEN', 'REMOVED', 'RESTORED', 'REPORT_DISMISSED')),
    ADD CONSTRAINT moderation_records_actor_type_check
        CHECK (actor_type IN ('AUTOMATIC', 'MODERATOR', 'SCOPE_OWNER'));

CREATE TABLE post_db.content_reports (
    id              UUID          PRIMARY KEY,
    target_type     VARCHAR(10)   NOT NULL CHECK (target_type IN ('POST', 'COMMENT')),
    target_id       UUID          NOT NULL,
    post_id         UUID          NOT NULL REFERENCES post_db.posts (id) ON DELETE CASCADE,
    reporter_id     UUID,
    reason          VARCHAR(30)   NOT NULL CHECK (reason IN ('BULLYING', 'HARASSMENT', 'THREAT', 'PERSONAL_DATA',
                                                             'ACADEMIC_INTEGRITY', 'SPAM', 'WRONG_CATEGORY', 'OTHER')),
    details         VARCHAR(1000),
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('OPEN', 'UPHELD', 'DISMISSED')),
    resolved_by     UUID,
    resolved_at     TIMESTAMPTZ,
    resolution_note VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_content_reports_open_reporter ON post_db.content_reports (target_type, target_id, reporter_id)
    WHERE status = 'OPEN';
CREATE INDEX idx_content_reports_status ON post_db.content_reports (status, created_at);
CREATE INDEX idx_content_reports_target ON post_db.content_reports (target_type, target_id);
CREATE INDEX idx_content_reports_reporter ON post_db.content_reports (reporter_id);
