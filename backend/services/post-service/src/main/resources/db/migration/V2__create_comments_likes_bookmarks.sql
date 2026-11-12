-- =====================================================
-- V2: comments, post_likes, post_bookmarks tablolarını oluştur
-- =====================================================

-- ═══════════════════════════════════════════════════════
-- COMMENTS TABLOSU
-- ═══════════════════════════════════════════════════════
-- parent_comment_id: Sadece üst seviye yoruma yanıt verilebilir (tek seviye derinlik).
-- status: Yorum oluşturulduğunda blacklist kontrolü yapılır; PUBLISHED veya REJECTED olur.

CREATE TABLE IF NOT EXISTS post_db.comments (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL,
    author_id           UUID            NOT NULL,
    parent_comment_id   UUID,
    content             TEXT            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PUBLISHED'
                                        CHECK (status IN ('PUBLISHED', 'REJECTED')),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,

    CONSTRAINT fk_comment_post
        FOREIGN KEY (post_id) REFERENCES post_db.posts(id) ON DELETE CASCADE,

    CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES post_db.comments(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comment_post_id     ON post_db.comments (post_id);
CREATE INDEX IF NOT EXISTS idx_comment_author_id   ON post_db.comments (author_id);
CREATE INDEX IF NOT EXISTS idx_comment_parent_id   ON post_db.comments (parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_status      ON post_db.comments (status);

COMMENT ON TABLE  post_db.comments IS 'Post yorumları tablosu. Tek seviye yanıt destekler (parent_comment_id).';
COMMENT ON COLUMN post_db.comments.author_id IS 'user-service e mantıksal referans (UUID). Fiziksel FK yok.';
COMMENT ON COLUMN post_db.comments.parent_comment_id IS 'Yanıt verilen üst yorum. NULL ise üst seviye yorumdur. Yanıta yanıt verilmez.';

-- ═══════════════════════════════════════════════════════
-- POST_LIKES TABLOSU
-- ═══════════════════════════════════════════════════════
-- Bir kullanıcı aynı post'u sadece bir kez beğenebilir (unique constraint).

CREATE TABLE IF NOT EXISTS post_db.post_likes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_like_post
        FOREIGN KEY (post_id) REFERENCES post_db.posts(id) ON DELETE CASCADE,

    CONSTRAINT uq_post_like_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_like_post_id ON post_db.post_likes (post_id);
CREATE INDEX IF NOT EXISTS idx_like_user_id ON post_db.post_likes (user_id);

COMMENT ON TABLE  post_db.post_likes IS 'Post beğeni tablosu. Her kullanıcı bir post''u en fazla bir kez beğenebilir.';
COMMENT ON COLUMN post_db.post_likes.user_id IS 'user-service e mantıksal referans (UUID). Fiziksel FK yok.';

-- ═══════════════════════════════════════════════════════
-- POST_BOOKMARKS TABLOSU
-- ═══════════════════════════════════════════════════════
-- Bir kullanıcı aynı post'u sadece bir kez kaydedebilir (unique constraint).

CREATE TABLE IF NOT EXISTS post_db.post_bookmarks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bookmark_post
        FOREIGN KEY (post_id) REFERENCES post_db.posts(id) ON DELETE CASCADE,

    CONSTRAINT uq_post_bookmark_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_bookmark_post_id ON post_db.post_bookmarks (post_id);
CREATE INDEX IF NOT EXISTS idx_bookmark_user_id ON post_db.post_bookmarks (user_id);

COMMENT ON TABLE  post_db.post_bookmarks IS 'Post kaydetme tablosu. Her kullanıcı bir post''u en fazla bir kez kaydedebilir.';
COMMENT ON COLUMN post_db.post_bookmarks.user_id IS 'user-service e mantıksal referans (UUID). Fiziksel FK yok.';

