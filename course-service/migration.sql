-- Course Service - Capacity kolonu ve yeni tablolar migration scripti
-- Çalıştırmak için:
-- docker exec -i educonnect-postgres psql -U eduadmin -d educonnect < course-service/migration.sql

-- 1. courses tablosuna capacity kolonu ekle (3 adımda - mevcut kayıtlar için)
-- Adım 1: Kolonu nullable olarak ekle
ALTER TABLE course_db.courses ADD COLUMN IF NOT EXISTS capacity INTEGER;
-- Adım 2: Mevcut kayıtlara varsayılan değer ata
UPDATE course_db.courses SET capacity = 30 WHERE capacity IS NULL;
-- Adım 3: NOT NULL constraint'i ekle
ALTER TABLE course_db.courses ALTER COLUMN capacity SET NOT NULL;
ALTER TABLE course_db.courses ALTER COLUMN capacity SET DEFAULT 30;

-- 2. course_announcements tablosu (eğer yoksa)
CREATE TABLE IF NOT EXISTS course_db.course_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- 3. course_applications tablosu (eğer yoksa)
CREATE TABLE IF NOT EXISTS course_db.course_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    application_date TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_date TIMESTAMP,
    processed_by UUID,
    rejection_reason VARCHAR(500),
    UNIQUE(course_id, student_id)
);


