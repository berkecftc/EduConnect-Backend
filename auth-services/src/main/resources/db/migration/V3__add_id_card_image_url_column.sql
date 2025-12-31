-- Add id_card_image_url column to academician_requests table
ALTER TABLE auth_db.academician_requests
ADD COLUMN IF NOT EXISTS id_card_image_url TEXT;

-- Add comment for documentation
COMMENT ON COLUMN auth_db.academician_requests.id_card_image_url IS 'URL of the academician ID card image stored in MinIO';

