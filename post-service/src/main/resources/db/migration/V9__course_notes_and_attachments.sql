ALTER TABLE post_db.posts
    ADD COLUMN course_label            VARCHAR(255),
    ADD COLUMN attachment_url          VARCHAR(1000),
    ADD COLUMN attachment_name         VARCHAR(255),
    ADD COLUMN declaration_accepted_at TIMESTAMPTZ;

ALTER TABLE post_db.posts DROP CONSTRAINT IF EXISTS posts_publisher_scope_check;
ALTER TABLE post_db.posts
    ADD CONSTRAINT posts_publisher_scope_check CHECK (
        (publisher_type = 'STUDENT' AND category <> 'DUYURU' AND club_id IS NULL
            AND (course_id IS NULL OR category IN ('SORU', 'DERS_NOTU')))
        OR (publisher_type = 'CLUB' AND category = 'DUYURU' AND club_id IS NOT NULL AND course_id IS NULL)
        OR (publisher_type = 'COURSE' AND category = 'DUYURU' AND course_id IS NOT NULL AND club_id IS NULL)
        OR (publisher_type = 'CAMPUS' AND category = 'DUYURU' AND club_id IS NULL AND course_id IS NULL)
    );
