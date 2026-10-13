CREATE TABLE activity_log (
    id         TEXT    PRIMARY KEY,
    created_at INTEGER NOT NULL,
    user_id    TEXT,
    user_name  TEXT,
    channel    TEXT,
    ip         TEXT,
    action     TEXT    NOT NULL,
    target     TEXT,
    details    TEXT
);
CREATE INDEX idx_activity_log_created ON activity_log (created_at);
