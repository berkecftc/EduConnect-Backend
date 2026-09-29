ALTER TABLE course_db.courses
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE course_db.course_applications ADD COLUMN version bigint NOT NULL DEFAULT 0;

DELETE FROM course_db.student_course_enrollments x
    WHERE NOT EXISTS (SELECT 1 FROM course_db.courses c WHERE c.id = x.course_id);
DELETE FROM course_db.course_applications x
    WHERE NOT EXISTS (SELECT 1 FROM course_db.courses c WHERE c.id = x.course_id);
DELETE FROM course_db.course_announcements x
    WHERE NOT EXISTS (SELECT 1 FROM course_db.courses c WHERE c.id = x.course_id);

ALTER TABLE course_db.student_course_enrollments
    ADD CONSTRAINT fk_student_course_enrollments_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE;
ALTER TABLE course_db.course_applications
    ADD CONSTRAINT fk_course_applications_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE;
ALTER TABLE course_db.course_announcements
    ADD CONSTRAINT fk_course_announcements_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE;

CREATE INDEX idx_courses_instructor ON course_db.courses (instructor_id);
CREATE INDEX idx_student_course_enrollments_student ON course_db.student_course_enrollments (student_id, is_active);
CREATE INDEX idx_course_applications_student ON course_db.course_applications (student_id);
CREATE INDEX idx_course_announcements_course_created ON course_db.course_announcements (course_id, created_at DESC);
