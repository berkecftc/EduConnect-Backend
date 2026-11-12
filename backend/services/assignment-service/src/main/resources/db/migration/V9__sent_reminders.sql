CREATE TABLE assignment_db.sent_reminders (
    reminder_key VARCHAR(200) PRIMARY KEY,
    sent_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
