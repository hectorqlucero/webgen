CREATE TABLE IF NOT EXISTS audit_log (
  id        INT AUTO_INCREMENT PRIMARY KEY,
  entity    VARCHAR(100) NOT NULL,
  operation VARCHAR(20)  NOT NULL,
  data      TEXT,
  user_id   INT,
  timestamp VARCHAR(50)
);
