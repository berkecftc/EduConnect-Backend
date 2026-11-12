ALTER TABLE club_db.club_memberships DROP CONSTRAINT ck_club_memberships_end_reason;
ALTER TABLE club_db.club_memberships ADD CONSTRAINT ck_club_memberships_end_reason
    CHECK (end_reason IN ('LEFT', 'EXPELLED', 'EXPIRED', 'FROZEN', 'AFFILIATION_ENDED'));

ALTER TABLE club_db.club_position_terms DROP CONSTRAINT ck_club_position_terms_reason;
ALTER TABLE club_db.club_position_terms ADD CONSTRAINT ck_club_position_terms_reason
    CHECK (end_reason IN ('CHANGED', 'RESIGNED', 'REMOVED', 'LEFT_CLUB', 'EXPELLED', 'HANDOVER', 'CLUB_CLOSED', 'ON_LEAVE', 'AFFILIATION_ENDED'));
