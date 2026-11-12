DELETE FROM event_db.events WHERE id::text LIKE '5eed%' OR club_id::text LIKE '5eed%';
DELETE FROM event_db.event_registrations WHERE student_id::text LIKE '5eed%';
DELETE FROM event_db.event_participation_requests WHERE student_id::text LIKE '5eed%';
