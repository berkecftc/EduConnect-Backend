ALTER TABLE user_db.students
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE user_db.academicians
    ADD COLUMN created_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamp with time zone NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
