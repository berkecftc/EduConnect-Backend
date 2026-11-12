CREATE TABLE club_db.club_approval_requests (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    type varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    prepared_by uuid,
    subject_user_id uuid,
    current_position varchar(40),
    requested_position varchar(40),
    note text,
    rejection_reason text,
    created_at timestamp with time zone NOT NULL,
    president_decided_by uuid,
    president_decided_at timestamp with time zone,
    decided_by uuid,
    decided_at timestamp with time zone,
    CONSTRAINT ck_club_approval_requests_type
        CHECK (type IN ('ROLE_CHANGE', 'RESIGNATION', 'ADVISOR_CHANGE', 'CLUB_CLOSURE')),
    CONSTRAINT ck_club_approval_requests_status
        CHECK (status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE UNIQUE INDEX ux_club_approval_requests_pending_subject
    ON club_db.club_approval_requests (club_id, type, subject_user_id)
    WHERE status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR') AND type IN ('ROLE_CHANGE', 'RESIGNATION');

CREATE UNIQUE INDEX ux_club_approval_requests_pending_club
    ON club_db.club_approval_requests (club_id, type)
    WHERE status IN ('PENDING_PRESIDENT', 'PENDING_ADVISOR') AND type IN ('ADVISOR_CHANGE', 'CLUB_CLOSURE');

CREATE INDEX idx_club_approval_requests_club_status ON club_db.club_approval_requests (club_id, status);
CREATE INDEX idx_club_approval_requests_subject_status ON club_db.club_approval_requests (subject_user_id, status);

INSERT INTO club_db.club_approval_requests (id, version, club_id, type, status, prepared_by, subject_user_id,
                                           current_position, requested_position, note, rejection_reason, created_at,
                                           decided_by, decided_at)
SELECT id, version, club_id, 'ROLE_CHANGE',
       CASE status WHEN 'PENDING' THEN 'PENDING_ADVISOR' ELSE status END,
       requester_id, student_id, previous_role, requested_role, NULL, rejection_reason,
       created_at AT TIME ZONE 'UTC', processed_by, processed_at AT TIME ZONE 'UTC'
FROM club_db.role_change_requests;

INSERT INTO club_db.club_approval_requests (id, version, club_id, type, status, prepared_by, subject_user_id,
                                           note, rejection_reason, created_at, decided_by, decided_at)
SELECT id, version, club_id, 'ADVISOR_CHANGE',
       CASE status WHEN 'PENDING' THEN 'PENDING_ADVISOR'
                   WHEN 'ACCEPTED' THEN 'APPROVED'
                   WHEN 'CANCELLED' THEN 'WITHDRAWN'
                   ELSE status END,
       requested_by, proposed_advisor_id, message, rejection_reason, created_at,
       CASE WHEN status IN ('ACCEPTED', 'REJECTED') THEN proposed_advisor_id END, decided_at
FROM club_db.advisor_change_requests;

DROP TABLE club_db.role_change_requests;
DROP TABLE club_db.advisor_change_requests;

CREATE TABLE club_db.club_decision_log (
    id uuid PRIMARY KEY,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    request_id uuid,
    request_type varchar(30),
    action varchar(40) NOT NULL,
    actor_id uuid,
    subject_user_id uuid,
    detail text,
    created_at timestamp with time zone NOT NULL
);

CREATE INDEX idx_club_decision_log_club_created ON club_db.club_decision_log (club_id, created_at DESC);
