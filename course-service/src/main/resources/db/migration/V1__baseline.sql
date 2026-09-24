CREATE TABLE course_db.courses (
    id uuid NOT NULL,
    capacity integer NOT NULL,
    code character varying(255) NOT NULL,
    credit integer NOT NULL,
    description text,
    image_url character varying(255),
    instructor_id uuid NOT NULL,
    semester character varying(255),
    title character varying(255) NOT NULL,
    CONSTRAINT courses_pkey PRIMARY KEY (id),
    CONSTRAINT uk61og8rbqdd2y28rx2et5fdnxd UNIQUE (code)
);

CREATE TABLE course_db.student_course_enrollments (
    id uuid NOT NULL,
    course_id uuid NOT NULL,
    enrollment_date timestamp(6) without time zone NOT NULL,
    is_active boolean NOT NULL,
    student_id uuid NOT NULL,
    CONSTRAINT student_course_enrollments_pkey PRIMARY KEY (id),
    CONSTRAINT uk8fs24asfn74x209dew05cv628 UNIQUE (course_id, student_id)
);

CREATE TABLE course_db.course_applications (
    id uuid NOT NULL,
    application_date timestamp(6) without time zone NOT NULL,
    course_id uuid NOT NULL,
    processed_by uuid,
    processed_date timestamp(6) without time zone,
    rejection_reason character varying(255),
    status character varying(255) NOT NULL,
    student_id uuid NOT NULL,
    CONSTRAINT course_applications_pkey PRIMARY KEY (id),
    CONSTRAINT ukspyhmpd8uvc739jmp55mgkpir UNIQUE (course_id, student_id),
    CONSTRAINT course_applications_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE course_db.course_announcements (
    id uuid NOT NULL,
    content text NOT NULL,
    course_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT course_announcements_pkey PRIMARY KEY (id)
);
