CREATE TABLE assignment_db.group_sets (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    course_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    self_signup boolean NOT NULL DEFAULT false,
    max_members integer,
    signup_closes_at timestamp(6) without time zone,
    created_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_sets_name UNIQUE (course_id, name),
    CONSTRAINT ck_group_sets_max_members CHECK (max_members IS NULL OR max_members BETWEEN 1 AND 100)
);

CREATE TABLE assignment_db.course_groups (
    id uuid PRIMARY KEY,
    group_set_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_groups_set FOREIGN KEY (group_set_id) REFERENCES assignment_db.group_sets (id) ON DELETE CASCADE,
    CONSTRAINT uq_course_groups_name UNIQUE (group_set_id, name)
);

CREATE TABLE assignment_db.group_members (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    group_set_id uuid NOT NULL,
    student_id uuid NOT NULL,
    added_by uuid,
    joined_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES assignment_db.course_groups (id) ON DELETE CASCADE,
    CONSTRAINT uq_group_members_set_student UNIQUE (group_set_id, student_id)
);

CREATE INDEX idx_group_sets_course ON assignment_db.group_sets (course_id);
CREATE INDEX idx_group_members_group ON assignment_db.group_members (group_id);
CREATE INDEX idx_group_members_student ON assignment_db.group_members (student_id);
