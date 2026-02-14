-- Fix student_requests table - make user_id nullable or drop it completely
-- This is needed because the new flow doesn't create users until approval

-- First, try to make user_id nullable (in case DROP fails due to dependencies)
ALTER TABLE auth_db.student_requests ALTER COLUMN user_id DROP NOT NULL;

-- If the column exists and we want to drop it completely, uncomment below:
-- ALTER TABLE auth_db.student_requests DROP COLUMN IF EXISTS user_id;

