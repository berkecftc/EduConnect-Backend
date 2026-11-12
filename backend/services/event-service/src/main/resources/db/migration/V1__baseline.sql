CREATE TABLE event_db.events (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    club_name character varying(255) NOT NULL,
    created_by_student_id uuid NOT NULL,
    description text,
    event_time timestamp(6) without time zone NOT NULL,
    image_url character varying(255),
    location character varying(255),
    status character varying(255),
    title character varying(255) NOT NULL,
    CONSTRAINT events_pkey PRIMARY KEY (id),
    CONSTRAINT events_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE event_db.event_registrations (
    id uuid NOT NULL,
    attended boolean NOT NULL,
    event_id uuid NOT NULL,
    qr_code character varying(255) NOT NULL,
    registration_time timestamp(6) without time zone,
    student_email character varying(255),
    student_id uuid NOT NULL,
    student_number character varying(255),
    CONSTRAINT event_registrations_pkey PRIMARY KEY (id),
    CONSTRAINT uki5xdpa4ftqlmt0r2rtwk00gsj UNIQUE (event_id, student_id),
    CONSTRAINT ukp0350tf2e0fm8wg2o3nr80ya0 UNIQUE (qr_code)
);

CREATE TABLE event_db.event_participation_requests (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    message text,
    processed_by uuid,
    processed_date timestamp(6) without time zone,
    rejection_reason text,
    request_date timestamp(6) without time zone NOT NULL,
    status character varying(255) NOT NULL,
    student_id uuid NOT NULL,
    CONSTRAINT event_participation_requests_pkey PRIMARY KEY (id),
    CONSTRAINT uk9tem8trufxvk438tbl0btp341 UNIQUE (event_id, student_id),
    CONSTRAINT event_participation_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
