ALTER TABLE post_db.posts
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE post_db.comments
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul',
    ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE post_db.post_likes
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul';

ALTER TABLE post_db.post_bookmarks
    ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'Europe/Istanbul';
