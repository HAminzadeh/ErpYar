CREATE TABLE approval_requests(id uuid PRIMARY KEY,company_id uuid NOT NULL,request_type varchar(60) NOT NULL,
 description varchar(1000) NOT NULL,requested_by varchar(36) NOT NULL,status varchar(20) NOT NULL,
 decided_by varchar(36),created_at timestamptz NOT NULL,decided_at timestamptz,version bigint NOT NULL DEFAULT 0);
CREATE INDEX idx_approvals_company_created ON approval_requests(company_id,created_at DESC);
