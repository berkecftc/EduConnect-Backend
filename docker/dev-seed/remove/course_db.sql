DELETE FROM course_db.courses WHERE id::text LIKE '5eed%' OR instructor_id::text LIKE '5eed%';
DELETE FROM course_db.student_course_enrollments WHERE student_id::text LIKE '5eed%';
DELETE FROM course_db.course_applications WHERE student_id::text LIKE '5eed%';
