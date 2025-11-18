-- Create schema if it does not exist so table lives alongside other club artifacts
CREATE SCHEMA IF NOT EXISTS club_db;

CREATE TABLE club_db.club_creation_requests (
    id UUID PRIMARY KEY,
    club_name VARCHAR(255) NOT NULL,
    about TEXT,
    requesting_student_id UUID NOT NULL,
    suggested_advisor_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    request_date TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE club_db.club_creation_requests
    ADD CONSTRAINT club_creation_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX idx_club_creation_requests_requesting_student
    ON club_db.club_creation_requests (requesting_student_id);

