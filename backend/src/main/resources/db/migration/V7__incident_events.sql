CREATE TABLE app.incident_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES app.incidents (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    type VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_incident_events_type CHECK (type IN (
        'INCIDENT_CREATED',
        'STATUS_CHANGED',
        'SEVERITY_CHANGED',
        'SERVICE_CHANGED',
        'COMMANDER_CHANGED',
        'TITLE_CHANGED',
        'DESCRIPTION_CHANGED'
    ))
);

CREATE INDEX idx_incident_events_org_incident_created
    ON app.incident_events (organization_id, incident_id, created_at DESC, id DESC);
