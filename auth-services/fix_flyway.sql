-- Flyway schema history'deki V1.1 migration kaydını sil
-- Bu script sadece geliştirme ortamında kullanılmalıdır!

-- Mevcut migration kayıtlarını göster
SELECT installed_rank, version, description, type, installed_on, success
FROM auth_db.flyway_schema_history
ORDER BY installed_rank;

-- V1.1 versiyonunu sil (eğer varsa)
DELETE FROM auth_db.flyway_schema_history
WHERE version = '1.1';

-- Güncel durumu göster
SELECT installed_rank, version, description, type, installed_on, success
FROM auth_db.flyway_schema_history
ORDER BY installed_rank;

