CREATE TABLE categories(id uuid PRIMARY KEY,company_id uuid NOT NULL,code varchar(40) NOT NULL,
 name varchar(160) NOT NULL,active boolean NOT NULL,UNIQUE(company_id,code));
CREATE INDEX idx_categories_company ON categories(company_id);
