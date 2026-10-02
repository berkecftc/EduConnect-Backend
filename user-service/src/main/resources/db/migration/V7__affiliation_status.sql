ALTER TABLE user_db.students
    ADD COLUMN enrollment_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_students_enrollment_status CHECK (enrollment_status IN
        ('ACTIVE', 'ON_LEAVE', 'GRADUATED', 'WITHDRAWN', 'EXPELLED', 'TRANSFERRED_OUT'));

ALTER TABLE user_db.academicians
    ADD COLUMN employment_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_academicians_employment_status CHECK (employment_status IN
        ('ACTIVE', 'ON_LEAVE', 'RETIRED', 'RESIGNED', 'TERMINATED'));
