ALTER TABLE club_db.club_memberships DROP CONSTRAINT IF EXISTS club_memberships_club_role_check;
ALTER TABLE club_db.role_change_requests DROP CONSTRAINT IF EXISTS role_change_requests_previous_role_check;
ALTER TABLE club_db.role_change_requests DROP CONSTRAINT IF EXISTS role_change_requests_requested_role_check;

UPDATE club_db.club_memberships SET club_role = CASE club_role
    WHEN 'ROLE_CLUB_OFFICIAL' THEN 'PRESIDENT'
    WHEN 'ROLE_VICE_PRESIDENT' THEN 'VICE_PRESIDENT'
    WHEN 'ROLE_SECRETARY' THEN 'GENERAL_SECRETARY'
    WHEN 'ROLE_TREASURER' THEN 'TREASURER'
    WHEN 'ROLE_BOARD_MEMBER' THEN 'BOARD_MEMBER'
    WHEN 'ROLE_MEMBER' THEN 'MEMBER'
    ELSE club_role
END;

UPDATE club_db.role_change_requests SET previous_role = CASE previous_role
    WHEN 'ROLE_CLUB_OFFICIAL' THEN 'PRESIDENT'
    WHEN 'ROLE_VICE_PRESIDENT' THEN 'VICE_PRESIDENT'
    WHEN 'ROLE_SECRETARY' THEN 'GENERAL_SECRETARY'
    WHEN 'ROLE_TREASURER' THEN 'TREASURER'
    WHEN 'ROLE_BOARD_MEMBER' THEN 'BOARD_MEMBER'
    WHEN 'ROLE_MEMBER' THEN 'MEMBER'
    ELSE previous_role
END,
requested_role = CASE requested_role
    WHEN 'ROLE_CLUB_OFFICIAL' THEN 'PRESIDENT'
    WHEN 'ROLE_VICE_PRESIDENT' THEN 'VICE_PRESIDENT'
    WHEN 'ROLE_SECRETARY' THEN 'GENERAL_SECRETARY'
    WHEN 'ROLE_TREASURER' THEN 'TREASURER'
    WHEN 'ROLE_BOARD_MEMBER' THEN 'BOARD_MEMBER'
    WHEN 'ROLE_MEMBER' THEN 'MEMBER'
    ELSE requested_role
END;

ALTER TABLE club_db.club_memberships ADD CONSTRAINT club_memberships_club_role_check
    CHECK (club_role IN ('PRESIDENT', 'VICE_PRESIDENT', 'GENERAL_SECRETARY', 'TREASURER', 'BOARD_MEMBER', 'MEMBER'));
ALTER TABLE club_db.role_change_requests ADD CONSTRAINT role_change_requests_previous_role_check
    CHECK (previous_role IN ('PRESIDENT', 'VICE_PRESIDENT', 'GENERAL_SECRETARY', 'TREASURER', 'BOARD_MEMBER', 'MEMBER'));
ALTER TABLE club_db.role_change_requests ADD CONSTRAINT role_change_requests_requested_role_check
    CHECK (requested_role IN ('PRESIDENT', 'VICE_PRESIDENT', 'GENERAL_SECRETARY', 'TREASURER', 'BOARD_MEMBER', 'MEMBER'));

CREATE UNIQUE INDEX uq_club_memberships_single_position
    ON club_db.club_memberships (club_id, club_role)
    WHERE is_active AND club_role IN ('PRESIDENT', 'VICE_PRESIDENT', 'GENERAL_SECRETARY', 'TREASURER');

CREATE UNIQUE INDEX uq_club_memberships_one_management_position
    ON club_db.club_memberships (student_id)
    WHERE is_active AND club_role <> 'MEMBER';

CREATE UNIQUE INDEX uq_role_change_requests_pending_student
    ON club_db.role_change_requests (club_id, student_id)
    WHERE status = 'PENDING';

ALTER TABLE club_db.club_membership_requests DROP CONSTRAINT IF EXISTS ukf19ptofj5w7owqxs7ts87ouyy;

CREATE UNIQUE INDEX uq_club_membership_requests_pending
    ON club_db.club_membership_requests (club_id, student_id)
    WHERE status = 'PENDING';
