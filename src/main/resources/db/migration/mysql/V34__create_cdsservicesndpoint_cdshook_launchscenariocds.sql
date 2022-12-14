# Create tables for CDS-Service-Endpoint, CDS-Hook, Joint table CDS-Service-Endpoint-CDS-Hook
CREATE TABLE cds_hook (
  id                    INT(11) NOT NULL AUTO_INCREMENT,
  logo_id               INT(11)          DEFAULT NULL,
  logo_uri              VARCHAR(255)     DEFAULT NULL,
  hook                  VARCHAR(255)     DEFAULT NULL,
  title                 VARCHAR(255)     DEFAULT NULL,
  description           VARCHAR(255)     DEFAULT NULL,
  hook_id               VARCHAR(255)     DEFAULT NULL,
  prefetch              JSON             DEFAULT NULL,
  cds_service_endpoint_id  INT(11)       DEFAULT NULL,
  hook_url              VARCHAR(255)     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY (logo_id),
  CONSTRAINT FOREIGN KEY (logo_id) REFERENCES image (id)
);

CREATE TABLE cds_service_endpoint (
  id                    INT(11) NOT NULL AUTO_INCREMENT,
  url                   VARCHAR(255)     DEFAULT NULL,
  title                 VARCHAR(255)     DEFAULT NULL,
  description           VARCHAR(255)     DEFAULT NULL,
  created_by_id         INT(11)          DEFAULT NULL,
  created_timestamp     DATETIME         DEFAULT NULL,
  sandbox_id            INT(11)          DEFAULT NULL,
  visibility            INT(11)          DEFAULT NULL,
  last_updated          DATETIME         DEFAULT NULL,
  PRIMARY KEY (id),
  KEY (created_by_id),
  KEY (sandbox_id),
  CONSTRAINT FOREIGN KEY (created_by_id) REFERENCES user (id),
  CONSTRAINT FOREIGN KEY (sandbox_id) REFERENCES sandbox (id)
);

ALTER TABLE launch_scenario ADD cds_hook_id INT(11) DEFAULT NULL;
ALTER TABLE launch_scenario ADD context JSON DEFAULT NULL;