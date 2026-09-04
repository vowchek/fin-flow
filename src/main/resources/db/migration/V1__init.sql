CREATE TABLE project (
    id              UUID PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    archived        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE time_entry (
    id              UUID PRIMARY KEY,
    project_id      UUID REFERENCES project (id),
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    note            VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT time_entry_duration_positive CHECK (duration_minutes >= 0),
    CONSTRAINT time_entry_range CHECK (ended_at >= started_at)
);

CREATE INDEX idx_time_entry_started_at ON time_entry (started_at);
CREATE INDEX idx_time_entry_project_id ON time_entry (project_id);

CREATE TABLE money_entry (
    id              UUID PRIMARY KEY,
    project_id      UUID REFERENCES project (id),
    direction       VARCHAR(16) NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    occurred_on     DATE NOT NULL,
    category        VARCHAR(80),
    note            VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT money_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT money_entry_direction CHECK (direction IN ('INCOME', 'EXPENSE'))
);

CREATE INDEX idx_money_entry_occurred_on ON money_entry (occurred_on);
CREATE INDEX idx_money_entry_project_id ON money_entry (project_id);
