ALTER TABLE course_db.course_applications
    DROP CONSTRAINT ukspyhmpd8uvc739jmp55mgkpir,
    DROP CONSTRAINT course_applications_status_check,
    ADD CONSTRAINT ck_course_applications_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CLOSED'));

CREATE UNIQUE INDEX uq_course_applications_pending
    ON course_db.course_applications (course_id, student_id) WHERE status = 'PENDING';
CREATE INDEX idx_course_applications_course ON course_db.course_applications (course_id, application_date);

ALTER TABLE course_db.student_course_enrollments
    ADD COLUMN withdrawn_at timestamp with time zone,
    ADD COLUMN withdrawn_by uuid,
    ADD COLUMN withdrawal_reason varchar(500);

CREATE TABLE course_db.course_enrollment_events (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL,
    student_id uuid NOT NULL,
    type varchar(12) NOT NULL,
    actor_id uuid,
    reason varchar(500),
    occurred_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_enrollment_events_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_course_enrollment_events_type CHECK (type IN ('ENROLLED', 'WITHDRAWN', 'REMOVED'))
);

CREATE INDEX idx_course_enrollment_events_course ON course_db.course_enrollment_events (course_id, occurred_at);
CREATE INDEX idx_course_enrollment_events_student ON course_db.course_enrollment_events (student_id, occurred_at);

INSERT INTO course_db.course_enrollment_events (id, course_id, student_id, type, actor_id, occurred_at)
SELECT gen_random_uuid(), e.course_id, e.student_id, 'ENROLLED', a.processed_by, e.enrollment_date
FROM course_db.student_course_enrollments e
LEFT JOIN course_db.course_applications a
       ON a.course_id = e.course_id AND a.student_id = e.student_id AND a.status = 'APPROVED';
