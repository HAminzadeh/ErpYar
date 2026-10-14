CREATE TABLE app_users (
  id uuid PRIMARY KEY, username varchar(80) NOT NULL UNIQUE, password_hash varchar(100) NOT NULL,
  display_name varchar(160) NOT NULL, active boolean NOT NULL DEFAULT true
);
CREATE TABLE user_roles (user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE, role varchar(255) NOT NULL);
CREATE TABLE user_holdings (user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE, holding_id varchar(36) NOT NULL);
CREATE TABLE user_companies (user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE, company_id varchar(36) NOT NULL);
CREATE INDEX user_roles_owner ON user_roles(user_id);
CREATE INDEX user_holdings_owner ON user_holdings(user_id);
CREATE INDEX user_companies_owner ON user_companies(user_id);
