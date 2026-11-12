ALTER TABLE club_db.clubs
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE club_db.club_memberships
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE club_db.club_membership_requests ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE club_db.role_change_requests ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE club_db.club_creation_requests ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE club_db.club_creation_requests
    ADD CONSTRAINT club_creation_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

DELETE FROM club_db.club_memberships m
    WHERE NOT EXISTS (SELECT 1 FROM club_db.clubs c WHERE c.id = m.club_id);
DELETE FROM club_db.club_membership_requests r
    WHERE NOT EXISTS (SELECT 1 FROM club_db.clubs c WHERE c.id = r.club_id);
DELETE FROM club_db.role_change_requests r
    WHERE NOT EXISTS (SELECT 1 FROM club_db.clubs c WHERE c.id = r.club_id);

ALTER TABLE club_db.club_memberships
    ADD CONSTRAINT fk_club_memberships_club FOREIGN KEY (club_id) REFERENCES club_db.clubs (id) ON DELETE CASCADE;
ALTER TABLE club_db.club_membership_requests
    ADD CONSTRAINT fk_club_membership_requests_club FOREIGN KEY (club_id) REFERENCES club_db.clubs (id) ON DELETE CASCADE;
ALTER TABLE club_db.role_change_requests
    ADD CONSTRAINT fk_role_change_requests_club FOREIGN KEY (club_id) REFERENCES club_db.clubs (id) ON DELETE CASCADE;

CREATE INDEX idx_clubs_academic_advisor ON club_db.clubs (academic_advisor_id);
CREATE INDEX idx_club_memberships_student ON club_db.club_memberships (student_id);
CREATE INDEX idx_club_membership_requests_club_status ON club_db.club_membership_requests (club_id, status);
CREATE INDEX idx_club_membership_requests_student_status ON club_db.club_membership_requests (student_id, status);
CREATE INDEX idx_role_change_requests_club_status ON club_db.role_change_requests (club_id, status);
CREATE INDEX idx_role_change_requests_student_status ON club_db.role_change_requests (student_id, status);
CREATE INDEX idx_club_creation_requests_status ON club_db.club_creation_requests (status);
CREATE INDEX idx_club_creation_requests_student_status ON club_db.club_creation_requests (requesting_student_id, status);
