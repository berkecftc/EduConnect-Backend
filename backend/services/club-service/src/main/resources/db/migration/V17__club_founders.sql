ALTER TABLE club_db.club_creation_requests
    DROP CONSTRAINT club_creation_requests_status_check,
    ADD CONSTRAINT club_creation_requests_status_check
        CHECK (status IN ('PENDING_FOUNDERS', 'PENDING', 'APPROVED', 'REJECTED')),
    ADD COLUMN club_id uuid REFERENCES club_db.clubs (id) ON DELETE SET NULL;

CREATE INDEX idx_club_creation_requests_club ON club_db.club_creation_requests (club_id);

CREATE TABLE club_db.club_founders (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES club_db.club_creation_requests (id) ON DELETE CASCADE,
    student_id uuid NOT NULL,
    status varchar(10) NOT NULL CHECK (status IN ('INVITED', 'CONFIRMED', 'DECLINED')),
    responded_at timestamp without time zone,
    CONSTRAINT uq_club_founders_request_student UNIQUE (request_id, student_id)
);

CREATE INDEX idx_club_founders_student ON club_db.club_founders (student_id, status);

INSERT INTO club_db.club_founders (id, request_id, student_id, status, responded_at)
SELECT gen_random_uuid(), r.id, r.requesting_student_id, 'CONFIRMED', r.request_date
FROM club_db.club_creation_requests r;
