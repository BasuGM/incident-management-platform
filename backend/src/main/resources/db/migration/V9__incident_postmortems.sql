CREATE TABLE app.incident_postmortems (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES app.incidents (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    impact TEXT NOT NULL DEFAULT '',
    root_cause TEXT NOT NULL DEFAULT '',
    resolution TEXT NOT NULL DEFAULT '',
    lessons_learned TEXT NOT NULL DEFAULT '',
    corrective_actions TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    published_by_id UUID REFERENCES app.users (id) ON DELETE RESTRICT,
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_incident_postmortems_incident UNIQUE (incident_id),
    CONSTRAINT chk_incident_postmortems_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_incident_postmortems_title_length CHECK (char_length(title) <= 200),
    CONSTRAINT chk_incident_postmortems_summary_length CHECK (char_length(summary) <= 10000),
    CONSTRAINT chk_incident_postmortems_impact_length CHECK (char_length(impact) <= 10000),
    CONSTRAINT chk_incident_postmortems_root_cause_length CHECK (char_length(root_cause) <= 10000),
    CONSTRAINT chk_incident_postmortems_resolution_length CHECK (char_length(resolution) <= 10000),
    CONSTRAINT chk_incident_postmortems_lessons_length CHECK (char_length(lessons_learned) <= 10000),
    CONSTRAINT chk_incident_postmortems_actions_length CHECK (char_length(corrective_actions) <= 10000)
);

CREATE INDEX idx_incident_postmortems_organization_status_published
    ON app.incident_postmortems (organization_id, status, published_at DESC NULLS LAST);

CREATE INDEX idx_incident_postmortems_organization_created
    ON app.incident_postmortems (organization_id, created_at DESC);
