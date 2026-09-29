DELETE FROM post_db.posts WHERE author_id::text LIKE '5eed%';
DELETE FROM post_db.comments WHERE author_id::text LIKE '5eed%';
DELETE FROM post_db.post_likes WHERE user_id::text LIKE '5eed%';
DELETE FROM post_db.post_bookmarks WHERE user_id::text LIKE '5eed%';
