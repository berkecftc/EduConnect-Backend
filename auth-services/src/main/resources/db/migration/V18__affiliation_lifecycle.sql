ALTER TABLE auth_db.users
    ADD COLUMN student_status varchar(20),
    ADD COLUMN staff_status varchar(20),
    ADD COLUMN closure_due_at timestamp with time zone,
    ADD CONSTRAINT ck_users_student_status CHECK (student_status IS NULL OR student_status IN
        ('ACTIVE', 'ON_LEAVE', 'GRADUATED', 'WITHDRAWN', 'EXPELLED', 'TRANSFERRED_OUT')),
    ADD CONSTRAINT ck_users_staff_status CHECK (staff_status IS NULL OR staff_status IN
        ('ACTIVE', 'ON_LEAVE', 'RETIRED', 'RESIGNED', 'TERMINATED'));

UPDATE auth_db.users u SET student_status = 'ACTIVE'
WHERE EXISTS (SELECT 1 FROM auth_db.user_roles r WHERE r.user_id = u.id AND r.role = 'ROLE_STUDENT');

UPDATE auth_db.users u SET staff_status = 'ACTIVE'
WHERE EXISTS (SELECT 1 FROM auth_db.user_roles r WHERE r.user_id = u.id AND r.role = 'ROLE_ACADEMICIAN');

CREATE INDEX idx_users_closure_due ON auth_db.users (closure_due_at) WHERE closure_due_at IS NOT NULL;

CREATE TABLE auth_db.affiliation_status_changes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    affiliation varchar(10) NOT NULL,
    previous_status varchar(20),
    status varchar(20) NOT NULL,
    effective_date date NOT NULL,
    reason varchar(1000),
    changed_by varchar(255),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_affiliation_status_changes_affiliation CHECK (affiliation IN ('STUDENT', 'STAFF')),
    CONSTRAINT fk_affiliation_status_changes_user FOREIGN KEY (user_id) REFERENCES auth_db.users (id) ON DELETE CASCADE
);

CREATE INDEX idx_affiliation_status_changes_user ON auth_db.affiliation_status_changes (user_id, created_at);
