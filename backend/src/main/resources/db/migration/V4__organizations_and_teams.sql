CREATE TABLE app.organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

CREATE INDEX idx_organizations_slug ON app.organizations (slug);

CREATE TABLE app.organization_members (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app.users (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organization_members_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT chk_organization_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
);

CREATE INDEX idx_organization_members_user_id ON app.organization_members (user_id);
CREATE INDEX idx_organization_members_organization_id ON app.organization_members (organization_id);

CREATE TABLE app.teams (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_teams_organization_name UNIQUE (organization_id, name)
);

CREATE INDEX idx_teams_organization_id ON app.teams (organization_id);

CREATE TABLE app.team_members (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES app.teams (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app.users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id)
);

CREATE INDEX idx_team_members_team_id ON app.team_members (team_id);
CREATE INDEX idx_team_members_user_id ON app.team_members (user_id);
