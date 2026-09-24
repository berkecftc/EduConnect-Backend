INSERT INTO user_db.academicians (id, first_name, last_name, email, title, department, office_number, bio) VALUES
    ('5eed0000-0000-4000-8000-00000000a001', 'Ayşe', 'Yılmaz', 'hoca1@seed.educonnect.local', 'Dr. Öğr. Üyesi', 'Bilgisayar Mühendisliği', 'B-204', 'Yazılım mühendisliği ve dağıtık sistemler.'),
    ('5eed0000-0000-4000-8000-00000000a002', 'Mehmet', 'Kaya', 'hoca2@seed.educonnect.local', 'Doç. Dr.', 'Bilgisayar Mühendisliği', 'B-310', 'Veritabanı sistemleri.')
ON CONFLICT DO NOTHING;

INSERT INTO user_db.students (id, first_name, last_name, email, student_number, department, bio) VALUES
    ('5eed0000-0000-4000-8000-00000000b001', 'Elif', 'Demir', 'ogrenci1@seed.educonnect.local', '2026000001', 'Bilgisayar Mühendisliği', 'Yazılım Kulübü başkanı.'),
    ('5eed0000-0000-4000-8000-00000000b002', 'Can', 'Şahin', 'ogrenci2@seed.educonnect.local', '2026000002', 'Bilgisayar Mühendisliği', NULL),
    ('5eed0000-0000-4000-8000-00000000b003', 'Zeynep', 'Çelik', 'ogrenci3@seed.educonnect.local', '2026000003', 'Bilgisayar Mühendisliği', NULL),
    ('5eed0000-0000-4000-8000-00000000b004', 'Burak', 'Arslan', 'ogrenci4@seed.educonnect.local', '2026000004', 'Elektrik-Elektronik Mühendisliği', NULL),
    ('5eed0000-0000-4000-8000-00000000b005', 'Selin', 'Koç', 'ogrenci5@seed.educonnect.local', '2026000005', 'Mimarlık', 'Fotoğrafçılık Kulübü başkanı.'),
    ('5eed0000-0000-4000-8000-00000000b006', 'Emre', 'Aydın', 'ogrenci6@seed.educonnect.local', '2026000006', 'Bilgisayar Mühendisliği', NULL),
    ('5eed0000-0000-4000-8000-00000000b007', 'Deniz', 'Öztürk', 'ogrenci7@seed.educonnect.local', '2026000007', 'Endüstri Mühendisliği', NULL),
    ('5eed0000-0000-4000-8000-00000000b008', 'Mert', 'Yıldız', 'ogrenci8@seed.educonnect.local', '2026000008', 'Matematik', 'Satranç Kulübü başkanı.')
ON CONFLICT DO NOTHING;
