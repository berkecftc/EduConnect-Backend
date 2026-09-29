DELETE FROM assignment_db.assignments WHERE id::text LIKE '5eed%' OR course_id::text LIKE '5eed%';
DELETE FROM assignment_db.assignment_submissions WHERE student_id::text LIKE '5eed%';
