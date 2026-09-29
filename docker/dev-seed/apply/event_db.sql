INSERT INTO event_db.events (id, club_id, club_name, created_by_student_id, title, description, event_time, location, status) VALUES
    ('5eed0000-0000-4000-8000-00000000e001', '5eed0000-0000-4000-8000-00000000c001', 'Yazılım Kulübü', '5eed0000-0000-4000-8000-00000000b001', 'Spring Boot Atölyesi', 'Sıfırdan REST API geliştirme.', date_trunc('hour', now()) + interval '7 days', 'Mühendislik Fakültesi B-101', 'ACTIVE'),
    ('5eed0000-0000-4000-8000-00000000e002', '5eed0000-0000-4000-8000-00000000c001', 'Yazılım Kulübü', '5eed0000-0000-4000-8000-00000000b001', 'Hackathon 2026', '24 saatlik takım yarışması.', date_trunc('hour', now()) + interval '20 days', 'Kütüphane Konferans Salonu', 'ACTIVE'),
    ('5eed0000-0000-4000-8000-00000000e003', '5eed0000-0000-4000-8000-00000000c001', 'Yazılım Kulübü', '5eed0000-0000-4000-8000-00000000b002', 'Kod İnceleme Buluşması', 'Açık kaynak projelerde kod inceleme pratiği.', date_trunc('hour', now()) + interval '10 days', 'B-204', 'PENDING'),
    ('5eed0000-0000-4000-8000-00000000e004', '5eed0000-0000-4000-8000-00000000c002', 'Fotoğrafçılık Kulübü', '5eed0000-0000-4000-8000-00000000b005', 'Fotoğraf Yürüyüşü', 'Tarihi yarımadada gün batımı çekimi.', date_trunc('hour', now()) + interval '5 days', 'Kampüs Ana Kapı', 'ACTIVE'),
    ('5eed0000-0000-4000-8000-00000000e005', '5eed0000-0000-4000-8000-00000000c003', 'Satranç Kulübü', '5eed0000-0000-4000-8000-00000000b008', 'Satranç Turnuvası', 'İsviçre sistemi, 5 tur.', date_trunc('hour', now()) + interval '14 days', 'Öğrenci Merkezi', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO event_db.event_registrations (id, event_id, student_id, qr_code, registration_time, attended) VALUES
    ('5eed0000-0000-4000-8000-00000000e101', '5eed0000-0000-4000-8000-00000000e001', '5eed0000-0000-4000-8000-00000000b003', 'seed-ticket-e101', now() - interval '2 days', false),
    ('5eed0000-0000-4000-8000-00000000e102', '5eed0000-0000-4000-8000-00000000e001', '5eed0000-0000-4000-8000-00000000b004', 'seed-ticket-e102', now() - interval '1 day', false),
    ('5eed0000-0000-4000-8000-00000000e103', '5eed0000-0000-4000-8000-00000000e001', '5eed0000-0000-4000-8000-00000000b006', 'seed-ticket-e103', now() - interval '5 hours', false),
    ('5eed0000-0000-4000-8000-00000000e104', '5eed0000-0000-4000-8000-00000000e004', '5eed0000-0000-4000-8000-00000000b006', 'seed-ticket-e104', now() - interval '3 days', false)
ON CONFLICT DO NOTHING;

INSERT INTO event_db.event_participation_requests (id, event_id, student_id, status, message, request_date) VALUES
    ('5eed0000-0000-4000-8000-00000000e201', '5eed0000-0000-4000-8000-00000000e002', '5eed0000-0000-4000-8000-00000000b007', 'PENDING', 'Takımım hazır.', now() - interval '1 day'),
    ('5eed0000-0000-4000-8000-00000000e202', '5eed0000-0000-4000-8000-00000000e002', '5eed0000-0000-4000-8000-00000000b008', 'PENDING', NULL, now() - interval '6 hours')
ON CONFLICT DO NOTHING;
