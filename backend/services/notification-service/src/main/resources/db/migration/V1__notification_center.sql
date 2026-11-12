CREATE SCHEMA IF NOT EXISTS notification_db;

CREATE TABLE notification_db.notifications (
    id           UUID          PRIMARY KEY,
    recipient_id UUID          NOT NULL,
    category     VARCHAR(20)   NOT NULL,
    type         VARCHAR(60)   NOT NULL,
    title        VARCHAR(255)  NOT NULL,
    body         TEXT          NOT NULL,
    link         VARCHAR(500),
    dedup_key    VARCHAR(200),
    email_status VARCHAR(12)   NOT NULL CHECK (email_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    created_at   TIMESTAMPTZ   NOT NULL,
    read_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_notifications_dedup ON notification_db.notifications (recipient_id, dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX idx_notifications_inbox ON notification_db.notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notification_db.notifications (recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_created ON notification_db.notifications (created_at);

CREATE TABLE notification_db.notification_preferences (
    user_id       UUID        NOT NULL,
    category      VARCHAR(20) NOT NULL,
    email_enabled BOOLEAN     NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, category)
);
