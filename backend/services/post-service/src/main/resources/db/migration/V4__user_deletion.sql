ALTER TABLE post_db.posts ALTER COLUMN author_id DROP NOT NULL;
ALTER TABLE post_db.comments ALTER COLUMN author_id DROP NOT NULL;
