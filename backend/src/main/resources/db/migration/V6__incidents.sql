-- Organization-scoped incident numbers: next_number is the value to assign after increment.
-- First allocation uses INSERT (next_number = 2) RETURNING 1; subsequent rows use ON CONFLICT upsert.
CREATE TABLE app.organization_incident_counters (
    organization_id UUID PRIMARY KEY REFERENCES app.organizations (id) ON DELETE CASCADE,
    next_number BIGINT NOT NULL
);

CREATE TABLE app.incidents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    service_id UUID REFERENCES app.services (id) ON DELETE RESTRICT,
    incident_number BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reporter_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    commander_id UUID REFERENCES app.users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT uq_incidents_organization_number UNIQUE (organization_id, incident_number),
    CONSTRAINT chk_incidents_severity CHECK (severity IN ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    CONSTRAINT chk_incidents_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CANCELLED'))
);

CREATE INDEX idx_incidents_organization_id ON app.incidents (organization_id);
CREATE INDEX idx_incidents_organization_created_at ON app.incidents (organization_id, created_at DESC);
CREATE INDEX idx_incidents_organization_status ON app.incidents (organization_id, status);
CREATE INDEX idx_incidents_organization_severity ON app.incidents (organization_id, severity);
CREATE INDEX idx_incidents_organization_service_id ON app.incidents (organization_id, service_id);
CREATE INDEX idx_incidents_organization_commander_id ON app.incidents (organization_id, commander_id);
