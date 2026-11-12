CREATE TABLE course_db.course_staff (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    course_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(12) NOT NULL,
    added_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_staff_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE,
    CONSTRAINT uq_course_staff_member UNIQUE (course_id, user_id),
    CONSTRAINT ck_course_staff_role CHECK (role IN ('INSTRUCTOR', 'ASSISTANT'))
);

CREATE INDEX idx_course_staff_user ON course_db.course_staff (user_id);
