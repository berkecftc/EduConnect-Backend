ALTER TABLE user_db.academicians
    ADD COLUMN academic_title varchar(30),
    ADD CONSTRAINT ck_academicians_title CHECK (academic_title IS NULL OR academic_title IN
        ('PROFESSOR', 'ASSOCIATE_PROFESSOR', 'ASSISTANT_PROFESSOR', 'LECTURER_PHD', 'LECTURER', 'RESEARCH_ASSISTANT_PHD', 'RESEARCH_ASSISTANT'));

UPDATE user_db.academicians a
SET academic_title = m.code, title = m.label
FROM (VALUES
          ('profdr', 'PROFESSOR', 'Prof. Dr.'),
          ('doçdr', 'ASSOCIATE_PROFESSOR', 'Doç. Dr.'),
          ('docdr', 'ASSOCIATE_PROFESSOR', 'Doç. Dr.'),
          ('dröğrüyesi', 'ASSISTANT_PROFESSOR', 'Dr. Öğr. Üyesi'),
          ('drogruyesi', 'ASSISTANT_PROFESSOR', 'Dr. Öğr. Üyesi'),
          ('yrddoçdr', 'ASSISTANT_PROFESSOR', 'Dr. Öğr. Üyesi'),
          ('yrddocdr', 'ASSISTANT_PROFESSOR', 'Dr. Öğr. Üyesi'),
          ('öğrgördr', 'LECTURER_PHD', 'Öğr. Gör. Dr.'),
          ('ogrgordr', 'LECTURER_PHD', 'Öğr. Gör. Dr.'),
          ('öğrgör', 'LECTURER', 'Öğr. Gör.'),
          ('ogrgor', 'LECTURER', 'Öğr. Gör.'),
          ('arşgördr', 'RESEARCH_ASSISTANT_PHD', 'Arş. Gör. Dr.'),
          ('arsgordr', 'RESEARCH_ASSISTANT_PHD', 'Arş. Gör. Dr.'),
          ('arşgör', 'RESEARCH_ASSISTANT', 'Arş. Gör.'),
          ('arsgor', 'RESEARCH_ASSISTANT', 'Arş. Gör.')
     ) AS m(normalized, code, label)
WHERE regexp_replace(lower(a.title), '[[:space:].]', '', 'g') = m.normalized;
