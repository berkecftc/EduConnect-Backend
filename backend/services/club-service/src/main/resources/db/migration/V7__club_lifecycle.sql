ALTER TABLE club_db.clubs
    ADD COLUMN status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN normalized_name varchar(255),
    ADD COLUMN closed_at timestamp with time zone,
    ADD COLUMN closure_reason text,
    ADD COLUMN closed_by uuid,
    ALTER COLUMN academic_advisor_id DROP NOT NULL;

UPDATE club_db.clubs
SET normalized_name = regexp_replace(
        lower(translate(btrim(name), 'İIıĞğÜüŞşÖöÇçÂâÎîÛû', 'iiigguussooccaaiiuu')),
        '\s+', ' ', 'g');

ALTER TABLE club_db.clubs
    ALTER COLUMN normalized_name SET NOT NULL,
    DROP CONSTRAINT uk7x7wph0bo1s930dmfh32t3soh,
    ADD CONSTRAINT ck_clubs_status CHECK (status IN ('ACTIVE', 'AWAITING_ADVISOR', 'CLOSED')),
    ADD CONSTRAINT ck_clubs_advisor_when_active CHECK (status <> 'ACTIVE' OR academic_advisor_id IS NOT NULL);

CREATE UNIQUE INDEX ux_clubs_normalized_name_open
    ON club_db.clubs (normalized_name)
    WHERE status <> 'CLOSED';

CREATE TABLE club_db.advisor_change_requests (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    club_id uuid NOT NULL REFERENCES club_db.clubs (id) ON DELETE CASCADE,
    proposed_advisor_id uuid NOT NULL,
    previous_advisor_id uuid,
    requested_by uuid,
    message text,
    status varchar(20) NOT NULL,
    rejection_reason text,
    created_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    CONSTRAINT ck_advisor_change_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX ux_advisor_change_requests_pending
    ON club_db.advisor_change_requests (club_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_advisor_change_requests_proposed
    ON club_db.advisor_change_requests (proposed_advisor_id, status);
