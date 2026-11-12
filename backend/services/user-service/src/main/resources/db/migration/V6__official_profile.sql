ALTER TABLE user_db.academicians ADD COLUMN office_hours varchar(500);

CREATE TABLE user_db.profile_change_requests (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    user_id uuid NOT NULL,
    first_name varchar(255),
    last_name varchar(255),
    academic_title varchar(30),
    program_id uuid,
    department_id uuid,
    reason varchar(1000) NOT NULL,
    status varchar(10) NOT NULL,
    review_note varchar(1000),
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_change_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_profile_change_program FOREIGN KEY (program_id) REFERENCES user_db.programs (id),
    CONSTRAINT fk_profile_change_department FOREIGN KEY (department_id) REFERENCES user_db.departments (id)
);

CREATE UNIQUE INDEX uq_profile_change_pending ON user_db.profile_change_requests (user_id) WHERE status = 'PENDING';
CREATE INDEX idx_profile_change_status ON user_db.profile_change_requests (status, created_at);
