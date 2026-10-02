CREATE TABLE auth_db.email_change_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash varchar(64) NOT NULL,
    user_id uuid NOT NULL,
    new_email varchar(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_change_tokens_hash UNIQUE (token_hash),
    CONSTRAINT uq_email_change_tokens_user UNIQUE (user_id),
    CONSTRAINT fk_email_change_tokens_user FOREIGN KEY (user_id) REFERENCES auth_db.users (id) ON DELETE CASCADE
);
