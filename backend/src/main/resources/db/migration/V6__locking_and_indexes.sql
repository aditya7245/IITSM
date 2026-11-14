-- Optimistic locking for incidents: a save that started from an out-of-date copy now fails instead of silently
-- overwriting a newer change (for example the SLA monitor and a technician acting on the same incident at once).
ALTER TABLE incidents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The application treats e-mail addresses case-insensitively; the database now enforces the same, so two
-- concurrent registrations of a@x and A@x cannot both succeed.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- Indexes for filters and pages that previously scanned whole tables.
CREATE INDEX idx_incidents_category       ON incidents(category_id);
CREATE INDEX idx_audit_created            ON audit_logs(created_at);
CREATE INDEX idx_audit_user               ON audit_logs(user_id);
CREATE INDEX idx_notifications_user_time  ON notifications(user_id, created_at);
