CREATE TABLE audit_events(id uuid PRIMARY KEY,company_id uuid NOT NULL,actor_id varchar(36) NOT NULL,
 action varchar(80) NOT NULL,entity_type varchar(80) NOT NULL,entity_id uuid NOT NULL,occurred_at timestamptz NOT NULL);
CREATE INDEX idx_audit_company_time ON audit_events(company_id,occurred_at DESC);
