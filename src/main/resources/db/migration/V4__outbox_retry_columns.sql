-- Outbox hardening: retry counter + processing lease (for dead-lettering and stuck-event reclaim).
ALTER TABLE outbox_event ADD COLUMN attempts     INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD COLUMN locked_until TIMESTAMPTZ;
