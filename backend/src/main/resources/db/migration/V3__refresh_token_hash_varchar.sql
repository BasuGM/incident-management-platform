-- Align token_hash with JPA (VARCHAR) after V2 used fixed-width CHAR for SHA-256 hex.
ALTER TABLE app.refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);
