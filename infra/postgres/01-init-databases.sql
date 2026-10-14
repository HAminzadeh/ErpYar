-- psql meta-command; runs outside a single transaction in docker-entrypoint-initdb.d
SELECT 'CREATE DATABASE identity_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='identity_db') \gexec
SELECT 'CREATE DATABASE organization_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='organization_db') \gexec
SELECT 'CREATE DATABASE master_data_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='master_data_db') \gexec
SELECT 'CREATE DATABASE workflow_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='workflow_db') \gexec
SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='notification_db') \gexec
SELECT 'CREATE DATABASE audit_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname='audit_db') \gexec
