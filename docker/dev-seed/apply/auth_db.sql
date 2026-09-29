INSERT INTO auth_db.users (id, email, password, status, email_verified_at) VALUES
    ('5eed0000-0000-4000-8000-00000000a001', 'hoca1@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000a002', 'hoca2@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b001', 'ogrenci1@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b002', 'ogrenci2@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b003', 'ogrenci3@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b004', 'ogrenci4@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b005', 'ogrenci5@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b006', 'ogrenci6@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b007', 'ogrenci7@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now()),
    ('5eed0000-0000-4000-8000-00000000b008', 'ogrenci8@seed.educonnect.local', '$2a$12$njPSjKjMehSZcqcDzDJ7tOS7KAukmWuW3FdPynXfsfqap9SpHuCZa', 'ACTIVE', now())
ON CONFLICT DO NOTHING;

INSERT INTO auth_db.user_roles (user_id, role) VALUES
    ('5eed0000-0000-4000-8000-00000000a001', 'ROLE_ACADEMICIAN'),
    ('5eed0000-0000-4000-8000-00000000a002', 'ROLE_ACADEMICIAN'),
    ('5eed0000-0000-4000-8000-00000000b001', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b001', 'ROLE_CLUB_OFFICIAL'),
    ('5eed0000-0000-4000-8000-00000000b002', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b002', 'ROLE_CLUB_OFFICIAL'),
    ('5eed0000-0000-4000-8000-00000000b003', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b004', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b005', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b005', 'ROLE_CLUB_OFFICIAL'),
    ('5eed0000-0000-4000-8000-00000000b006', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b007', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b008', 'ROLE_STUDENT'),
    ('5eed0000-0000-4000-8000-00000000b008', 'ROLE_CLUB_OFFICIAL')
ON CONFLICT DO NOTHING;
