ALTER TABLE post_db.posts
    ADD COLUMN accepted_comment_id UUID REFERENCES post_db.comments (id) ON DELETE SET NULL;
