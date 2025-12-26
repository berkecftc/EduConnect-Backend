-- Comprehensive Flyway Schema History Fix Script
-- Bu script geliştirme ortamında Flyway migration sorunlarını çözmek için kullanılır
-- UYARI: Production ortamında kullanmayın!

-- 1. Önce mevcut migration kayıtlarını göster
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM auth_db.flyway_schema_history
ORDER BY installed_rank;

-- 2. Sorunlu migration kayıtlarını sil
-- V1.1 versiyonunu sil (dosya artık mevcut değil)
DELETE FROM auth_db.flyway_schema_history
WHERE version = '1.1';

-- V2 migration kaydını da sil (checksum uyuşmazlığı için)
DELETE FROM auth_db.flyway_schema_history
WHERE version = '2';

-- 3. Tüm migration geçmişini temizlemek için (isteğe bağlı - sadece development için!)
-- UYARI: Bu tüm migration geçmişini siler!
-- DELETE FROM auth_db.flyway_schema_history;

-- 4. Güncel durumu kontrol et
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM auth_db.flyway_schema_history
ORDER BY installed_rank;

-- 5. Flyway'in yeniden başlatılmasını sağlamak için baseline oluştur (isteğe bağlı)
-- INSERT INTO auth_db.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
-- VALUES (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'eduadmin', CURRENT_TIMESTAMP, 0, true);

