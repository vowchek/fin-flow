CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(120),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

ALTER TABLE project ADD COLUMN owner_id UUID;
ALTER TABLE time_entry ADD COLUMN owner_id UUID;
ALTER TABLE money_entry ADD COLUMN owner_id UUID;

ALTER TABLE project
    ADD CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);
ALTER TABLE time_entry
    ADD CONSTRAINT fk_time_entry_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);
ALTER TABLE money_entry
    ADD CONSTRAINT fk_money_entry_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);

CREATE INDEX idx_project_owner_id ON project (owner_id);
CREATE INDEX idx_time_entry_owner_id ON time_entry (owner_id);
CREATE INDEX idx_money_entry_owner_id ON money_entry (owner_id);
