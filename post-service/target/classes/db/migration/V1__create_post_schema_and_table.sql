-- =====================================================
-- V1: post_db şemasını ve posts tablosunu oluştur
-- =====================================================

-- Şema oluştur
CREATE SCHEMA IF NOT EXISTS post_db;

-- Posts tablosu
CREATE TABLE IF NOT EXISTS post_db.posts (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255)    NOT NULL,
    content     TEXT            NOT NULL,
    category    VARCHAR(20)     NOT NULL CHECK (category IN ('DUYURU', 'DERS_NOTU', 'SORU')),
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'REJECTED')),
    author_id   UUID            NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

-- İndeksler
CREATE INDEX IF NOT EXISTS idx_post_title     ON post_db.posts (title);
CREATE INDEX IF NOT EXISTS idx_post_status    ON post_db.posts (status);
CREATE INDEX IF NOT EXISTS idx_post_author_id ON post_db.posts (author_id);

COMMENT ON TABLE  post_db.posts IS 'Blog / içerik paylaşım tablosu. user_schema ile fiziksel FK bağlantısı YOKTUR.';
COMMENT ON COLUMN post_db.posts.author_id IS 'user-service e mantıksal referans (UUID). Fiziksel FK yok.';


