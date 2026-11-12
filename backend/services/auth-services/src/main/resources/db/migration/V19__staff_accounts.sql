ALTER TABLE auth_db.users ADD COLUMN display_name varchar(200);

CREATE TABLE auth_db.staff_permission_grants (
    user_id uuid NOT NULL,
    permission varchar(30) NOT NULL,
    faculty_id uuid,
    CONSTRAINT fk_staff_permission_grants_user FOREIGN KEY (user_id) REFERENCES auth_db.users (id) ON DELETE CASCADE,
    CONSTRAINT ck_staff_permission_grants_permission CHECK (permission IN
        ('STUDENT_VERIFIER', 'STAFF_VERIFIER', 'MODERATOR', 'CAMPUS_PUBLISHER', 'ACCOUNT_MANAGER')),
    CONSTRAINT ck_staff_permission_grants_scope CHECK (faculty_id IS NULL OR permission = 'STUDENT_VERIFIER')
);

CREATE UNIQUE INDEX uq_staff_permission_grants ON auth_db.staff_permission_grants
    (user_id, permission, coalesce(faculty_id, '00000000-0000-0000-0000-000000000000'::uuid));

ALTER TABLE auth_db.user_roles DROP CONSTRAINT user_roles_role_check;
ALTER TABLE auth_db.user_roles ADD CONSTRAINT user_roles_role_check CHECK (role IN (
    'ROLE_STUDENT', 'ROLE_PENDING_STUDENT', 'ROLE_ACADEMICIAN', 'ROLE_PENDING_ACADEMICIAN',
    'ROLE_CLUB_OFFICIAL', 'ROLE_PENDING_CLUB_OFFICIAL', 'ROLE_ADMIN', 'ROLE_STAFF'));
