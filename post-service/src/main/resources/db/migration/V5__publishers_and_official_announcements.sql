ALTER TABLE post_db.posts DROP CONSTRAINT IF EXISTS posts_category_check;
ALTER TABLE post_db.posts DROP CONSTRAINT IF EXISTS posts_status_check;

ALTER TABLE post_db.posts
    ADD COLUMN publisher_type    VARCHAR(20)   NOT NULL DEFAULT 'STUDENT',
    ADD COLUMN club_id           UUID,
    ADD COLUMN course_id         UUID,
    ADD COLUMN publisher_name    VARCHAR(255),
    ADD COLUMN comments_disabled BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN approved_by       UUID,
    ADD COLUMN approved_at       TIMESTAMPTZ,
    ADD COLUMN review_note       VARCHAR(1000);

UPDATE post_db.posts SET category = 'GENEL' WHERE category = 'DUYURU';

ALTER TABLE post_db.posts
    ADD CONSTRAINT posts_category_check CHECK (category IN ('SORU', 'GENEL', 'DERS_NOTU', 'DUYURU')),
    ADD CONSTRAINT posts_status_check CHECK (status IN ('AWAITING_APPROVAL', 'PENDING', 'PUBLISHED', 'REJECTED')),
    ADD CONSTRAINT posts_publisher_type_check CHECK (publisher_type IN ('STUDENT', 'CLUB', 'COURSE', 'CAMPUS')),
    ADD CONSTRAINT posts_publisher_scope_check CHECK (
        (publisher_type = 'STUDENT' AND category <> 'DUYURU' AND club_id IS NULL)
        OR (publisher_type = 'CLUB' AND category = 'DUYURU' AND club_id IS NOT NULL AND course_id IS NULL)
        OR (publisher_type = 'COURSE' AND category = 'DUYURU' AND course_id IS NOT NULL AND club_id IS NULL)
        OR (publisher_type = 'CAMPUS' AND category = 'DUYURU' AND club_id IS NULL AND course_id IS NULL)
    );

CREATE INDEX idx_post_category_status ON post_db.posts (category, status, created_at DESC);
CREATE INDEX idx_post_club ON post_db.posts (club_id) WHERE club_id IS NOT NULL;
CREATE INDEX idx_post_course ON post_db.posts (course_id) WHERE course_id IS NOT NULL;
