INSERT INTO club_db.clubs (id, name, normalized_name, about, academic_advisor_id) VALUES
    ('5eed0000-0000-4000-8000-00000000c001', 'Yazılım Kulübü', 'yazilim kulubu', 'Atölyeler, hackathonlar ve açık kaynak projeler.', '5eed0000-0000-4000-8000-00000000a001'),
    ('5eed0000-0000-4000-8000-00000000c002', 'Fotoğrafçılık Kulübü', 'fotografcilik kulubu', 'Kampüs ve şehir fotoğraf yürüyüşleri.', '5eed0000-0000-4000-8000-00000000a001'),
    ('5eed0000-0000-4000-8000-00000000c003', 'Satranç Kulübü', 'satranc kulubu', 'Haftalık turnuvalar ve eğitimler.', '5eed0000-0000-4000-8000-00000000a002')
ON CONFLICT DO NOTHING;

UPDATE club_db.clubs SET logo_url = 'club-bucket/logos/' || id || '.png'
WHERE id IN ('5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000c002', '5eed0000-0000-4000-8000-00000000c003')
  AND logo_url IS NULL;

INSERT INTO club_db.club_memberships (id, club_id, student_id, club_role, is_active, term_start_date) VALUES
    ('5eed0000-0000-4000-8000-00000000d001', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b001', 'PRESIDENT', true, now() - interval '120 days'),
    ('5eed0000-0000-4000-8000-00000000d002', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b002', 'VICE_PRESIDENT', true, now() - interval '100 days'),
    ('5eed0000-0000-4000-8000-00000000d003', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b003', 'MEMBER', true, now() - interval '60 days'),
    ('5eed0000-0000-4000-8000-00000000d004', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b004', 'MEMBER', true, now() - interval '30 days'),
    ('5eed0000-0000-4000-8000-00000000d005', '5eed0000-0000-4000-8000-00000000c002', '5eed0000-0000-4000-8000-00000000b005', 'PRESIDENT', true, now() - interval '90 days'),
    ('5eed0000-0000-4000-8000-00000000d006', '5eed0000-0000-4000-8000-00000000c002', '5eed0000-0000-4000-8000-00000000b006', 'MEMBER', true, now() - interval '20 days'),
    ('5eed0000-0000-4000-8000-00000000d007', '5eed0000-0000-4000-8000-00000000c003', '5eed0000-0000-4000-8000-00000000b008', 'PRESIDENT', true, now() - interval '200 days'),
    ('5eed0000-0000-4000-8000-00000000d008', '5eed0000-0000-4000-8000-00000000c003', '5eed0000-0000-4000-8000-00000000b003', 'MEMBER', true, now() - interval '15 days')
ON CONFLICT DO NOTHING;

INSERT INTO club_db.club_membership_requests (id, club_id, student_id, status, message, request_date) VALUES
    ('5eed0000-0000-4000-8000-00000000d101', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b006', 'PENDING', 'Backend geliştirmeye ilgim var.', now() - interval '3 days'),
    ('5eed0000-0000-4000-8000-00000000d102', '5eed0000-0000-4000-8000-00000000c001', '5eed0000-0000-4000-8000-00000000b007', 'PENDING', 'Hackathon ekibine katılmak istiyorum.', now() - interval '1 day'),
    ('5eed0000-0000-4000-8000-00000000d103', '5eed0000-0000-4000-8000-00000000c002', '5eed0000-0000-4000-8000-00000000b007', 'PENDING', NULL, now() - interval '2 days')
ON CONFLICT DO NOTHING;

INSERT INTO club_db.club_approval_requests (id, version, club_id, type, status, prepared_by, subject_user_id, current_position, requested_position, created_at) VALUES
    ('5eed0000-0000-4000-8000-00000000d201', 0, '5eed0000-0000-4000-8000-00000000c001', 'ROLE_CHANGE', 'PENDING_ADVISOR', '5eed0000-0000-4000-8000-00000000b001', '5eed0000-0000-4000-8000-00000000b003', 'MEMBER', 'BOARD_MEMBER', now() - interval '2 days')
ON CONFLICT DO NOTHING;
