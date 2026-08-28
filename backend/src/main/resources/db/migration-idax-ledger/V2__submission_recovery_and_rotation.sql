ALTER TABLE idax_ledger.ledger_submission
    ADD COLUMN sequence_number BIGINT,
    ADD COLUMN last_ledger_sequence BIGINT,
    ADD COLUMN anchoring_account VARCHAR(64),
    ADD COLUMN reconciled_at TIMESTAMPTZ;

CREATE INDEX idx_ledger_submission_recovery
    ON idax_ledger.ledger_submission(status, next_retry_at, updated_at)
    WHERE status IN ('SIGNED', 'SUBMITTED', 'FAILED_RETRYABLE');
