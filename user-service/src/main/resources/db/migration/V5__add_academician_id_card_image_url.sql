-- Add id_card_image_url column to academicians table
ALTER TABLE user_db.academicians
ADD COLUMN IF NOT EXISTS id_card_image_url TEXT;

-- Add comment for documentation
COMMENT ON COLUMN user_db.academicians.id_card_image_url IS 'URL of the academician ID card image stored in MinIO';

