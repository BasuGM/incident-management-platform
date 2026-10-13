CREATE TABLE app.on_call_schedules (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES app.organizations (id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    rotation_length_days SMALLINT NOT NULL,
    handoff_local_time TIME NOT NULL,
    handoff_day_of_week SMALLINT,
    effective_from DATE NOT NULL,
    team_id UUID REFERENCES app.teams (id) ON DELETE RESTRICT,
    service_id UUID REFERENCES app.services (id) ON DELETE RESTRICT,
    created_by_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    updated_by_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_on_call_schedules_organization_name UNIQUE (organization_id, name),
    CONSTRAINT chk_on_call_schedules_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_on_call_schedules_rotation_length CHECK (rotation_length_days IN (1, 7)),
    CONSTRAINT chk_on_call_schedules_scope CHECK (team_id IS NULL OR service_id IS NULL),
    CONSTRAINT chk_on_call_schedules_handoff_dow CHECK (
        (rotation_length_days = 7
            AND handoff_day_of_week IS NOT NULL
            AND handoff_day_of_week BETWEEN 1 AND 7)
        OR (rotation_length_days = 1 AND handoff_day_of_week IS NULL)
    ),
    CONSTRAINT chk_on_call_schedules_timezone_length CHECK (char_length(timezone) <= 64),
    CONSTRAINT chk_on_call_schedules_name_length CHECK (char_length(name) <= 100)
);

CREATE INDEX idx_on_call_schedules_organization_id ON app.on_call_schedules (organization_id);
CREATE INDEX idx_on_call_schedules_organization_status ON app.on_call_schedules (organization_id, status);
CREATE INDEX idx_on_call_schedules_organization_team_id ON app.on_call_schedules (organization_id, team_id);
CREATE INDEX idx_on_call_schedules_organization_service_id ON app.on_call_schedules (organization_id, service_id);

CREATE TABLE app.on_call_schedule_participants (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES app.on_call_schedules (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app.users (id) ON DELETE RESTRICT,
    position INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_on_call_schedule_participants_schedule_user UNIQUE (schedule_id, user_id),
    CONSTRAINT uq_on_call_schedule_participants_schedule_position UNIQUE (schedule_id, position),
    CONSTRAINT chk_on_call_schedule_participants_position CHECK (position >= 0)
);

CREATE INDEX idx_on_call_schedule_participants_schedule_id ON app.on_call_schedule_participants (schedule_id);
CREATE INDEX idx_on_call_schedule_participants_user_id ON app.on_call_schedule_participants (user_id);
