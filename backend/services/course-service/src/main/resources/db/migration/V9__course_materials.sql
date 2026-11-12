CREATE TABLE course_db.course_materials (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    course_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    section varchar(100),
    sort_order integer NOT NULL DEFAULT 0,
    kind varchar(8) NOT NULL,
    file_url varchar(255),
    link_url varchar(2000),
    visible boolean NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_materials_course FOREIGN KEY (course_id) REFERENCES course_db.courses (id) ON DELETE CASCADE,
    CONSTRAINT ck_course_materials_kind CHECK (kind IN ('FILE', 'LINK')),
    CONSTRAINT ck_course_materials_target CHECK ((kind = 'FILE' AND file_url IS NOT NULL) OR (kind = 'LINK' AND link_url IS NOT NULL))
);

CREATE INDEX idx_course_materials_course ON course_db.course_materials (course_id, sort_order);
