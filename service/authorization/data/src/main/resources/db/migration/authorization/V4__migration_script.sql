ALTER TABLE exhausted_event ADD COLUMN replay_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE authentication
    ADD COLUMN last_active_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN dormancy_stage INT NOT NULL DEFAULT 0,
    ADD COLUMN dormancy_warned_at TIMESTAMP(6) NULL,
    ADD INDEX authentication_last_active_at (last_active_at);
