ALTER TABLE post_db.moderation_records DROP CONSTRAINT IF EXISTS moderation_records_action_check;
ALTER TABLE post_db.moderation_records
    ADD CONSTRAINT moderation_records_action_check
        CHECK (action IN ('PUBLISHED', 'REJECTED', 'SENT_TO_REVIEW', 'HIDDEN', 'REMOVED', 'RESTORED', 'REPORT_DISMISSED',
                          'APPEAL_ACCEPTED', 'APPEAL_REJECTED'));

CREATE TABLE post_db.moderation_appeals (
    id                  UUID          PRIMARY KEY,
    target_type         VARCHAR(10)   NOT NULL CHECK (target_type IN ('POST', 'COMMENT')),
    target_id           UUID          NOT NULL,
    post_id             UUID          NOT NULL REFERENCES post_db.posts (id) ON DELETE CASCADE,
    appellant_id        UUID,
    statement           VARCHAR(2000) NOT NULL,
    appealed_action     VARCHAR(20)   NOT NULL CHECK (appealed_action IN ('REJECTED', 'HIDDEN', 'REMOVED')),
    original_decider_id UUID,
    status              VARCHAR(10)   NOT NULL CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED')),
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    decision_note       VARCHAR(1000),
    created_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_moderation_appeals_target UNIQUE (target_type, target_id)
);

CREATE INDEX idx_moderation_appeals_status ON post_db.moderation_appeals (status, created_at);
CREATE INDEX idx_moderation_appeals_appellant ON post_db.moderation_appeals (appellant_id, created_at DESC);
