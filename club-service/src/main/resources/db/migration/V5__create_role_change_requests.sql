-- V5__create_role_change_requests.sql
-- Kulüp görev değişikliği talepleri tablosu

CREATE TABLE IF NOT EXISTS club_db.role_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL,
    student_id UUID NOT NULL,
    previous_role VARCHAR(50),
    requested_role VARCHAR(50) NOT NULL,
    requester_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processed_by UUID,

    -- Foreign key kısıtlamaları (club_id için)
    CONSTRAINT fk_role_change_club
        FOREIGN KEY (club_id)
        REFERENCES club_db.clubs(id)
        ON DELETE CASCADE,

    -- Status değerlerini kısıtla
    CONSTRAINT chk_role_change_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- Aynı kulüpte aynı öğrenci için birden fazla PENDING talep olamaz (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_pending_request_per_student
    ON club_db.role_change_requests(club_id, student_id)
    WHERE status = 'PENDING';

-- Aynı kulüpte aynı rol için birden fazla PENDING talep olamaz (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_pending_request_per_role
    ON club_db.role_change_requests(club_id, requested_role)
    WHERE status = 'PENDING';

-- İndeksler
CREATE INDEX IF NOT EXISTS idx_role_change_club_id
    ON club_db.role_change_requests(club_id);

CREATE INDEX IF NOT EXISTS idx_role_change_student_id
    ON club_db.role_change_requests(student_id);

CREATE INDEX IF NOT EXISTS idx_role_change_status
    ON club_db.role_change_requests(status);

CREATE INDEX IF NOT EXISTS idx_role_change_requester_id
    ON club_db.role_change_requests(requester_id);

CREATE INDEX IF NOT EXISTS idx_role_change_club_status
    ON club_db.role_change_requests(club_id, status);

-- Açıklamalar
COMMENT ON TABLE club_db.role_change_requests IS 'Kulüp görev değişikliği talepleri - Danışman onayı gerektirir';
COMMENT ON COLUMN club_db.role_change_requests.previous_role IS 'Öğrencinin mevcut rolü';
COMMENT ON COLUMN club_db.role_change_requests.requested_role IS 'Talep edilen yeni rol';
COMMENT ON COLUMN club_db.role_change_requests.requester_id IS 'Talebi oluşturan kulüp yetkilisi';
COMMENT ON COLUMN club_db.role_change_requests.processed_by IS 'Talebi onaylayan/reddeden danışman';




