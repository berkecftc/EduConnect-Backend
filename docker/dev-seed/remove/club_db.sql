DELETE FROM club_db.clubs WHERE id::text LIKE '5eed%' OR academic_advisor_id::text LIKE '5eed%';
DELETE FROM club_db.club_memberships WHERE student_id::text LIKE '5eed%';
DELETE FROM club_db.club_membership_requests WHERE student_id::text LIKE '5eed%';
DELETE FROM club_db.role_change_requests WHERE student_id::text LIKE '5eed%' OR requester_id::text LIKE '5eed%';
DELETE FROM club_db.club_creation_requests WHERE requesting_student_id::text LIKE '5eed%';
