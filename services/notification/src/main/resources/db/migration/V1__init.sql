CREATE TABLE in_app_notifications(id uuid PRIMARY KEY,company_id uuid NOT NULL,recipient_id varchar(36) NOT NULL,
 title varchar(160) NOT NULL,body varchar(2000) NOT NULL,read_at timestamptz,created_at timestamptz NOT NULL);
CREATE INDEX idx_inapp_target ON in_app_notifications(company_id,recipient_id,created_at DESC);
