-- Add bio (hakkimda) field to students and academicians if missing
ALTER TABLE user_db.students
ADD COLUMN IF NOT EXISTS bio TEXT;

ALTER TABLE user_db.academicians
ADD COLUMN IF NOT EXISTS bio TEXT;

COMMENT ON COLUMN user_db.students.bio IS 'Student about me text';
COMMENT ON COLUMN user_db.academicians.bio IS 'Academician about me text';

