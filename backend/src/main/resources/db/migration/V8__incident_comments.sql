CREATE TABLE app.incident_comments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES app.incidents (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_incident_comments_body_length CHECK (char_length(body) <= 5000)
);

CREATE INDEX idx_incident_comments_org_incident_created
    ON app.incident_comments (organization_id, incident_id, created_at ASC, id ASC);
