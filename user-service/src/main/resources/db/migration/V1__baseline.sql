CREATE TABLE user_db.students (
    id uuid NOT NULL,
    bio character varying(255),
    department character varying(255),
    email character varying(255),
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    profile_image_url character varying(255),
    student_document_url character varying(255),
    student_number character varying(255),
    CONSTRAINT students_pkey PRIMARY KEY (id),
    CONSTRAINT uke2rndfrsx22acpq2ty1caeuyw UNIQUE (email),
    CONSTRAINT ukh7gboo6v79gig1eo7lt1fubew UNIQUE (student_number)
);

CREATE TABLE user_db.academicians (
    id uuid NOT NULL,
    bio character varying(255),
    department character varying(255),
    email character varying(255),
    first_name character varying(255) NOT NULL,
    id_card_image_url character varying(255),
    last_name character varying(255) NOT NULL,
    office_number character varying(255),
    profile_image_url character varying(255),
    title character varying(255),
    CONSTRAINT academicians_pkey PRIMARY KEY (id),
    CONSTRAINT ukbs0aov7jj3sheifaq00dw2b88 UNIQUE (email)
);

CREATE TABLE user_db.archived_students (
    archive_id uuid NOT NULL,
    deleted_at timestamp(6) without time zone NOT NULL,
    deletion_reason character varying(1000),
    department character varying(255),
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    original_id uuid NOT NULL,
    profile_image_url character varying(255),
    student_number character varying(255),
    CONSTRAINT archived_students_pkey PRIMARY KEY (archive_id)
);

CREATE TABLE user_db.archived_academicians (
    archive_id uuid NOT NULL,
    deleted_at timestamp(6) without time zone NOT NULL,
    deletion_reason character varying(1000),
    department character varying(255),
    first_name character varying(255) NOT NULL,
    last_name character varying(255) NOT NULL,
    office_number character varying(255),
    original_id uuid NOT NULL,
    profile_image_url character varying(255),
    title character varying(255),
    CONSTRAINT archived_academicians_pkey PRIMARY KEY (archive_id)
);
