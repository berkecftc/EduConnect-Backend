UPDATE course_db.catalog_courses cc
SET code = replace(cc.code, 'İ', 'I')
WHERE cc.code LIKE '%İ%'
  AND NOT EXISTS (SELECT 1 FROM course_db.catalog_courses other WHERE other.code = replace(cc.code, 'İ', 'I'));

UPDATE course_db.courses c
SET code = cc.code
FROM course_db.catalog_courses cc
WHERE c.catalog_course_id = cc.id
  AND c.code <> cc.code
  AND c.code LIKE '%İ%';
