CREATE TABLE sharing_consent (
    user_id TEXT PRIMARY KEY NOT NULL,
    version INTEGER NOT NULL,
    granted_at_ms INTEGER NOT NULL
);

CREATE TABLE daily_watch (
    user_id TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    watch_date TEXT NOT NULL,
    shortform_minutes INTEGER NOT NULL CHECK (shortform_minutes BETWEEN 0 AND 1500),
    coverage REAL CHECK (coverage BETWEEN 0 AND 1),
    measured_at_ms INTEGER NOT NULL,
    saved_at_ms INTEGER NOT NULL,
    PRIMARY KEY (user_id, watch_date)
);

CREATE INDEX daily_watch_by_date ON daily_watch (watch_date);
