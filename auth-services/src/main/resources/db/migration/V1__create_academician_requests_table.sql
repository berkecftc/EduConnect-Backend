-- Create academician_requests table
CREATE TABLE IF NOT EXISTS auth_db.academician_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    title VARCHAR(255),
    department VARCHAR(255),
    office_number VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add index on user_id for better query performance
CREATE INDEX IF NOT EXISTS idx_academician_requests_user_id ON auth_db.academician_requests(user_id);

