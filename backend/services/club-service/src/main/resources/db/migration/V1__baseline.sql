CREATE TABLE club_db.clubs (
    id uuid NOT NULL,
    about text,
    academic_advisor_id uuid NOT NULL,
    logo_url character varying(255),
    name character varying(255) NOT NULL,
    CONSTRAINT clubs_pkey PRIMARY KEY (id),
    CONSTRAINT uk7x7wph0bo1s930dmfh32t3soh UNIQUE (name)
);

CREATE TABLE club_db.club_memberships (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    club_role character varying(255) NOT NULL,
    is_active boolean NOT NULL,
    student_id uuid NOT NULL,
    term_end_date timestamp(6) without time zone,
    term_start_date timestamp(6) without time zone,
    CONSTRAINT club_memberships_pkey PRIMARY KEY (id),
    CONSTRAINT uk9psekukpldfm6r3qh0ymf7bx0 UNIQUE (club_id, student_id),
    CONSTRAINT club_memberships_club_role_check CHECK (club_role IN ('ROLE_CLUB_OFFICIAL', 'ROLE_VICE_PRESIDENT', 'ROLE_SECRETARY', 'ROLE_TREASURER', 'ROLE_BOARD_MEMBER', 'ROLE_MEMBER'))
);

CREATE TABLE club_db.club_membership_requests (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    message text,
    processed_by uuid,
    processed_date timestamp(6) without time zone,
    rejection_reason text,
    request_date timestamp(6) without time zone NOT NULL,
    status character varying(255) NOT NULL,
    student_id uuid NOT NULL,
    CONSTRAINT club_membership_requests_pkey PRIMARY KEY (id),
    CONSTRAINT ukf19ptofj5w7owqxs7ts87ouyy UNIQUE (club_id, student_id),
    CONSTRAINT club_membership_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE club_db.role_change_requests (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    previous_role character varying(255),
    processed_at timestamp(6) without time zone,
    processed_by uuid,
    rejection_reason character varying(255),
    requested_role character varying(255) NOT NULL,
    requester_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    student_id uuid NOT NULL,
    CONSTRAINT role_change_requests_pkey PRIMARY KEY (id),
    CONSTRAINT role_change_requests_previous_role_check CHECK (previous_role IN ('ROLE_CLUB_OFFICIAL', 'ROLE_VICE_PRESIDENT', 'ROLE_SECRETARY', 'ROLE_TREASURER', 'ROLE_BOARD_MEMBER', 'ROLE_MEMBER')),
    CONSTRAINT role_change_requests_requested_role_check CHECK (requested_role IN ('ROLE_CLUB_OFFICIAL', 'ROLE_VICE_PRESIDENT', 'ROLE_SECRETARY', 'ROLE_TREASURER', 'ROLE_BOARD_MEMBER', 'ROLE_MEMBER')),
    CONSTRAINT role_change_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE club_db.club_creation_requests (
    id uuid NOT NULL,
    about text,
    club_name character varying(255) NOT NULL,
    request_date timestamp(6) without time zone,
    requesting_student_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    suggested_advisor_id uuid,
    CONSTRAINT club_creation_requests_pkey PRIMARY KEY (id)
);

CREATE TABLE club_db.archived_clubs (
    archive_id uuid NOT NULL,
    about text,
    academic_advisor_id uuid,
    deleted_at timestamp(6) without time zone NOT NULL,
    deleted_by_admin_id uuid,
    deletion_reason character varying(1000),
    logo_url character varying(255),
    name character varying(255) NOT NULL,
    original_id uuid NOT NULL,
    CONSTRAINT archived_clubs_pkey PRIMARY KEY (archive_id)
);
