CREATE TABLE course_db.terms (
    id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0,
    academic_year integer NOT NULL CHECK (academic_year BETWEEN 2000 AND 2200),
    season varchar(10) NOT NULL CHECK (season IN ('FALL', 'SPRING', 'SUMMER')),
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    enrollment_opens_on date,
    enrollment_closes_on date,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_terms_year_season UNIQUE (academic_year, season),
    CONSTRAINT ck_terms_dates CHECK (starts_on < ends_on),
    CONSTRAINT ck_terms_enrollment CHECK (enrollment_opens_on IS NULL OR enrollment_closes_on IS NULL
        OR enrollment_opens_on <= enrollment_closes_on)
);

INSERT INTO course_db.terms (id, academic_year, season, starts_on, ends_on)
VALUES ('7e000000-0000-4000-8000-000000002027', 2027, 'FALL', DATE '2026-09-14', DATE '2027-01-31');

CREATE TABLE course_db.catalog_courses (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    title varchar(255) NOT NULL,
    credit integer NOT NULL CHECK (credit >= 0),
    ects integer CHECK (ects IS NULL OR ects BETWEEN 0 AND 60),
    created_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

INSERT INTO course_db.catalog_courses (id, code, title, credit, created_by, created_at)
SELECT DISTINCT ON (upper(trim(c.code))) gen_random_uuid(), upper(trim(c.code)), c.title, c.credit, c.instructor_id, c.created_at
FROM course_db.courses c
ORDER BY upper(trim(c.code)), c.created_at;

ALTER TABLE course_db.courses
    ADD COLUMN term_id uuid,
    ADD COLUMN catalog_course_id uuid,
    ADD COLUMN section varchar(10) NOT NULL DEFAULT '1';

UPDATE course_db.courses c
SET catalog_course_id = cc.id,
    term_id = '7e000000-0000-4000-8000-000000002027',
    code = cc.code,
    semester = '2026-2027 Güz'
FROM course_db.catalog_courses cc
WHERE cc.code = upper(trim(c.code));

UPDATE course_db.courses c
SET section = ranked.section
FROM (SELECT id, row_number() OVER (PARTITION BY catalog_course_id ORDER BY created_at, id)::text AS section
      FROM course_db.courses) ranked
WHERE ranked.id = c.id;

ALTER TABLE course_db.courses
    ALTER COLUMN term_id SET NOT NULL,
    ALTER COLUMN catalog_course_id SET NOT NULL,
    DROP CONSTRAINT uk61og8rbqdd2y28rx2et5fdnxd,
    ADD CONSTRAINT fk_courses_term FOREIGN KEY (term_id) REFERENCES course_db.terms (id),
    ADD CONSTRAINT fk_courses_catalog FOREIGN KEY (catalog_course_id) REFERENCES course_db.catalog_courses (id),
    ADD CONSTRAINT uq_courses_offering UNIQUE (catalog_course_id, term_id, section);

CREATE INDEX idx_courses_term ON course_db.courses (term_id);
