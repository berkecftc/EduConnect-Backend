CREATE TABLE user_db.outbox_messages (
    id uuid NOT NULL,
    exchange character varying(255) NOT NULL,
    routing_key character varying(255) NOT NULL,
    body bytea NOT NULL,
    content_type character varying(100),
    content_encoding character varying(50),
    headers text,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    sent_at timestamp with time zone,
    attempts integer NOT NULL DEFAULT 0,
    last_error character varying(500),
    CONSTRAINT outbox_messages_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_messages_pending ON user_db.outbox_messages (created_at, id) WHERE sent_at IS NULL;
CREATE INDEX idx_outbox_messages_sent ON user_db.outbox_messages (sent_at) WHERE sent_at IS NOT NULL;
