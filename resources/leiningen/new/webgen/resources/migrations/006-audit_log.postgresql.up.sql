CREATE TABLE IF NOT EXISTS audit_log (
  id        SERIAL PRIMARY KEY,
  entity    TEXT        NOT NULL,
  operation TEXT        NOT NULL,
  data      TEXT,
  user_id   INTEGER,
  timestamp TEXT
);
