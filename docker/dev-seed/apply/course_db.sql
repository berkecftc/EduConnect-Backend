INSERT INTO course_db.courses (id, code, title, description, credit, semester, capacity, instructor_id) VALUES
    ('5eed0000-0000-4000-8000-00000000f001', 'SEED101', 'Programlamaya Giriş', 'Değişkenler, koşullar, döngüler ve fonksiyonlar.', 4, '2026 Güz', 40, '5eed0000-0000-4000-8000-00000000a001'),
    ('5eed0000-0000-4000-8000-00000000f002', 'SEED202', 'Veri Yapıları', 'Liste, yığın, kuyruk, ağaç ve çizgeler.', 4, '2026 Güz', 30, '5eed0000-0000-4000-8000-00000000a001'),
    ('5eed0000-0000-4000-8000-00000000f003', 'SEED303', 'Veritabanı Sistemleri', 'İlişkisel model, SQL ve normalizasyon.', 3, '2026 Güz', 25, '5eed0000-0000-4000-8000-00000000a002')
ON CONFLICT DO NOTHING;

INSERT INTO course_db.student_course_enrollments (id, course_id, student_id, enrollment_date, is_active) VALUES
    ('5eed0000-0000-4000-8000-00000000f101', '5eed0000-0000-4000-8000-00000000f001', '5eed0000-0000-4000-8000-00000000b001', now() - interval '30 days', true),
    ('5eed0000-0000-4000-8000-00000000f102', '5eed0000-0000-4000-8000-00000000f001', '5eed0000-0000-4000-8000-00000000b002', now() - interval '30 days', true),
    ('5eed0000-0000-4000-8000-00000000f103', '5eed0000-0000-4000-8000-00000000f001', '5eed0000-0000-4000-8000-00000000b003', now() - interval '29 days', true),
    ('5eed0000-0000-4000-8000-00000000f104', '5eed0000-0000-4000-8000-00000000f001', '5eed0000-0000-4000-8000-00000000b004', now() - interval '28 days', true),
    ('5eed0000-0000-4000-8000-00000000f105', '5eed0000-0000-4000-8000-00000000f001', '5eed0000-0000-4000-8000-00000000b005', now() - interval '28 days', true),
    ('5eed0000-0000-4000-8000-00000000f106', '5eed0000-0000-4000-8000-00000000f002', '5eed0000-0000-4000-8000-00000000b001', now() - interval '25 days', true),
    ('5eed0000-0000-4000-8000-00000000f107', '5eed0000-0000-4000-8000-00000000f002', '5eed0000-0000-4000-8000-00000000b002', now() - interval '25 days', true),
    ('5eed0000-0000-4000-8000-00000000f108', '5eed0000-0000-4000-8000-00000000f002', '5eed0000-0000-4000-8000-00000000b003', now() - interval '24 days', true),
    ('5eed0000-0000-4000-8000-00000000f109', '5eed0000-0000-4000-8000-00000000f003', '5eed0000-0000-4000-8000-00000000b006', now() - interval '20 days', true),
    ('5eed0000-0000-4000-8000-00000000f110', '5eed0000-0000-4000-8000-00000000f003', '5eed0000-0000-4000-8000-00000000b007', now() - interval '20 days', true)
ON CONFLICT DO NOTHING;

INSERT INTO course_db.course_applications (id, course_id, student_id, status, application_date) VALUES
    ('5eed0000-0000-4000-8000-00000000f201', '5eed0000-0000-4000-8000-00000000f002', '5eed0000-0000-4000-8000-00000000b004', 'PENDING', now() - interval '2 days'),
    ('5eed0000-0000-4000-8000-00000000f202', '5eed0000-0000-4000-8000-00000000f002', '5eed0000-0000-4000-8000-00000000b005', 'PENDING', now() - interval '1 day'),
    ('5eed0000-0000-4000-8000-00000000f203', '5eed0000-0000-4000-8000-00000000f003', '5eed0000-0000-4000-8000-00000000b008', 'PENDING', now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO course_db.course_announcements (id, course_id, title, content, created_by, created_at) VALUES
    ('5eed0000-0000-4000-8000-00000000f301', '5eed0000-0000-4000-8000-00000000f001', 'Ders programı', 'Laboratuvar saatleri perşembe 14:00''e alındı.', '5eed0000-0000-4000-8000-00000000a001', now() - interval '6 days'),
    ('5eed0000-0000-4000-8000-00000000f302', '5eed0000-0000-4000-8000-00000000f001', 'Ödev 1 yayınlandı', 'Teslim tarihine dikkat edin.', '5eed0000-0000-4000-8000-00000000a001', now() - interval '2 days'),
    ('5eed0000-0000-4000-8000-00000000f303', '5eed0000-0000-4000-8000-00000000f003', 'İlk hafta', 'Kitabın 1. ve 2. bölümlerini okuyun.', '5eed0000-0000-4000-8000-00000000a002', now() - interval '10 days')
ON CONFLICT DO NOTHING;
