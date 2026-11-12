CREATE TABLE user_db.faculties (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    code varchar(20) NOT NULL,
    name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_faculties_code UNIQUE (code)
);

CREATE TABLE user_db.departments (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    faculty_id uuid NOT NULL,
    code varchar(20) NOT NULL,
    name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_departments_faculty FOREIGN KEY (faculty_id) REFERENCES user_db.faculties (id),
    CONSTRAINT uq_departments_code UNIQUE (code)
);

CREATE TABLE user_db.programs (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    department_id uuid NOT NULL,
    code varchar(20) NOT NULL,
    name varchar(200) NOT NULL,
    level varchar(12) NOT NULL,
    duration_years integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT fk_programs_department FOREIGN KEY (department_id) REFERENCES user_db.departments (id),
    CONSTRAINT uq_programs_code UNIQUE (code),
    CONSTRAINT ck_programs_level CHECK (level IN ('ASSOCIATE', 'BACHELOR', 'MASTER', 'DOCTORATE')),
    CONSTRAINT ck_programs_duration CHECK (duration_years BETWEEN 1 AND 8)
);

CREATE INDEX idx_departments_faculty ON user_db.departments (faculty_id);
CREATE INDEX idx_programs_department ON user_db.programs (department_id);

ALTER TABLE user_db.students
    ADD COLUMN program_id uuid,
    ADD COLUMN entry_year integer,
    ADD CONSTRAINT fk_students_program FOREIGN KEY (program_id) REFERENCES user_db.programs (id),
    ADD CONSTRAINT ck_students_entry_year CHECK (entry_year IS NULL OR entry_year BETWEEN 1950 AND 2200);

ALTER TABLE user_db.academicians
    ADD COLUMN department_id uuid,
    ADD CONSTRAINT fk_academicians_department FOREIGN KEY (department_id) REFERENCES user_db.departments (id);

CREATE INDEX idx_students_program ON user_db.students (program_id);
CREATE INDEX idx_academicians_department ON user_db.academicians (department_id);
