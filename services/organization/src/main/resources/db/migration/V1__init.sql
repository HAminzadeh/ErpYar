CREATE TABLE holdings(id uuid PRIMARY KEY, code varchar(40) NOT NULL UNIQUE,name varchar(200) NOT NULL,active boolean NOT NULL);
CREATE TABLE companies(id uuid PRIMARY KEY,holding_id uuid NOT NULL REFERENCES holdings(id),code varchar(40) NOT NULL UNIQUE,
 name varchar(200) NOT NULL,business_type varchar(30) NOT NULL,active boolean NOT NULL);
CREATE INDEX idx_company_holding ON companies(holding_id);
CREATE TABLE departments(id uuid PRIMARY KEY,company_id uuid NOT NULL REFERENCES companies(id),parent_id uuid REFERENCES departments(id),
 code varchar(40) NOT NULL,name varchar(160) NOT NULL,UNIQUE(company_id,code));
CREATE INDEX idx_department_company ON departments(company_id);
