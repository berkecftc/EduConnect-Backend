ALTER TABLE club_db.club_memberships DROP CONSTRAINT club_memberships_club_role_check;
ALTER TABLE club_db.club_memberships ADD CONSTRAINT club_memberships_club_role_check
    CHECK (club_role IN ('PRESIDENT', 'VICE_PRESIDENT', 'GENERAL_SECRETARY', 'TREASURER', 'BOARD_MEMBER', 'AUDITOR',
                         'EVENT_COORDINATOR', 'COMMUNICATIONS_OFFICER', 'MEMBERSHIP_OFFICER', 'SPONSORSHIP_OFFICER',
                         'MEMBER'));

ALTER TABLE club_db.club_membership_requests
    ADD COLUMN recommendation varchar(20),
    ADD COLUMN recommendation_note text,
    ADD COLUMN recommended_by uuid,
    ADD COLUMN recommended_at timestamp(6) without time zone,
    ADD CONSTRAINT ck_club_membership_requests_recommendation CHECK (recommendation IN ('APPROVE', 'REJECT'));
