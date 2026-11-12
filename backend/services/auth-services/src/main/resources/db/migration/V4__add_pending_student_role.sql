-- Update student_requests table for new registration flow
-- Add password column to store hashed password until approval
ALTER TABLE auth_db.student_requests ADD COLUMN IF NOT EXISTS password VARCHAR(255);

-- Remove user_id column as it's no longer needed (users are created only after approval)
ALTER TABLE auth_db.student_requests DROP COLUMN IF EXISTS user_id;

-- Add unique constraint on email to prevent duplicate applications
ALTER TABLE auth_db.student_requests DROP CONSTRAINT IF EXISTS student_requests_email_unique;
ALTER TABLE auth_db.student_requests ADD CONSTRAINT student_requests_email_unique UNIQUE (email);

-- Also update the role check constraint to include ROLE_PENDING_STUDENT for future use
ALTER TABLE auth_db.user_roles DROP CONSTRAINT IF EXISTS user_roles_role_check;
ALTER TABLE auth_db.user_roles ADD CONSTRAINT user_roles_role_check
CHECK (role IN (
    'ROLE_STUDENT',
    'ROLE_PENDING_STUDENT',
    'ROLE_ACADEMICIAN',
    'ROLE_PENDING_ACADEMICIAN',
    'ROLE_CLUB_OFFICIAL',
    'ROLE_PENDING_CLUB_OFFICIAL',
    'ROLE_ADMIN'
));

