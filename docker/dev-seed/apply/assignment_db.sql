INSERT INTO assignment_db.assignments (id, course_id, title, description, due_date) VALUES
    ('5eed0000-0000-4000-8000-00000001a001', '5eed0000-0000-4000-8000-00000000f001', 'Ödev 1: Değişkenler', 'Temel veri tipleriyle hesap makinesi.', date_trunc('hour', now()) + interval '7 days'),
    ('5eed0000-0000-4000-8000-00000001a002', '5eed0000-0000-4000-8000-00000000f001', 'Ödev 2: Döngüler', 'Asal sayı bulan program.', date_trunc('hour', now()) + interval '14 days'),
    ('5eed0000-0000-4000-8000-00000001a003', '5eed0000-0000-4000-8000-00000000f002', 'Bağlı Liste Uygulaması', 'Tek yönlü bağlı liste ve testleri.', date_trunc('hour', now()) + interval '10 days'),
    ('5eed0000-0000-4000-8000-00000001a004', '5eed0000-0000-4000-8000-00000000f003', 'ER Diyagramı', 'Kütüphane sistemi için ER diyagramı.', date_trunc('hour', now()) + interval '12 days')
ON CONFLICT DO NOTHING;

INSERT INTO assignment_db.assignment_submissions (id, assignment_id, student_id, submitted_at, is_late, grade, feedback) VALUES
    ('5eed0000-0000-4000-8000-00000001a101', '5eed0000-0000-4000-8000-00000001a001', '5eed0000-0000-4000-8000-00000000b001', now() - interval '1 day', false, 90, 'Temiz ve okunaklı.'),
    ('5eed0000-0000-4000-8000-00000001a102', '5eed0000-0000-4000-8000-00000001a001', '5eed0000-0000-4000-8000-00000000b002', now() - interval '5 hours', false, NULL, NULL),
    ('5eed0000-0000-4000-8000-00000001a103', '5eed0000-0000-4000-8000-00000001a003', '5eed0000-0000-4000-8000-00000000b003', now() - interval '2 hours', false, NULL, NULL)
ON CONFLICT DO NOTHING;
