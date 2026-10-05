CREATE TABLE app.services (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    team_id UUID REFERENCES app.teams (id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_services_organization_slug UNIQUE (organization_id, slug)
);

CREATE INDEX idx_services_organization_id ON app.services (organization_id);
CREATE INDEX idx_services_team_id ON app.services (team_id);
