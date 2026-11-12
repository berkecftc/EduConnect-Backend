CREATE TABLE assignment_db.assignments (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    description text,
    due_date timestamp(6) without time zone,
    file_url character varying(255),
    title character varying(255) NOT NULL,
    CONSTRAINT assignments_pkey PRIMARY KEY (id)
);

CREATE TABLE assignment_db.assignment_submissions (
    id uuid NOT NULL,
    assignment_id uuid NOT NULL,
    feedback text,
    grade integer,
    is_late boolean NOT NULL,
    student_id uuid NOT NULL,
    submission_file_url character varying(255),
    submitted_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT assignment_submissions_pkey PRIMARY KEY (id),
    CONSTRAINT ukjpaoiqlq2bm3rv52lcri47g4s UNIQUE (assignment_id, student_id)
);
