CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS idax_ledger;

CREATE TABLE idax_ledger.ledger_proof (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    external_id VARCHAR(255) NOT NULL,
    proof_type VARCHAR(100) NOT NULL,
    hash_algorithm VARCHAR(20) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    canonicalization_profile VARCHAR(100) NOT NULL,
    format_version SMALLINT NOT NULL DEFAULT 1,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ledger_proof_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_ledger_proof_semantic UNIQUE (tenant_id, external_id, proof_type, content_hash),
    CONSTRAINT ck_ledger_proof_sha256 CHECK (hash_algorithm = 'SHA-256' AND content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ledger_proof_format CHECK (format_version = 1)
);

CREATE TABLE idax_ledger.ledger_submission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id),
    proof_id UUID NOT NULL REFERENCES idax_ledger.ledger_proof(id) ON DELETE CASCADE,
    network_id VARCHAR(100) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    last_error_detail VARCHAR(1000),
    transaction_hash VARCHAR(64),
    signed_transaction_blob TEXT,
    ledger_index BIGINT,
    ledger_hash VARCHAR(64),
    submitted_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_ledger_submission_network UNIQUE (proof_id, network_id)
);
CREATE UNIQUE INDEX uq_ledger_submission_tx ON idax_ledger.ledger_submission(network_id, transaction_hash)
    WHERE transaction_hash IS NOT NULL;
CREATE INDEX idx_ledger_proof_tenant_public ON idax_ledger.ledger_proof(tenant_id, public_id);
CREATE INDEX idx_ledger_submission_tenant_proof ON idax_ledger.ledger_submission(tenant_id, proof_id);

ALTER TABLE idax_ledger.ledger_proof ENABLE ROW LEVEL SECURITY;
ALTER TABLE idax_ledger.ledger_submission ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_ledger_proof_admin ON idax_ledger.ledger_proof FOR ALL TO idax_admin USING (true) WITH CHECK (true);
CREATE POLICY p_ledger_proof_app ON idax_ledger.ledger_proof FOR ALL TO idax_app
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY p_ledger_submission_admin ON idax_ledger.ledger_submission FOR ALL TO idax_admin USING (true) WITH CHECK (true);
CREATE POLICY p_ledger_submission_app ON idax_ledger.ledger_submission FOR ALL TO idax_app
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT USAGE ON SCHEMA idax_ledger TO idax_app, idax_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA idax_ledger TO idax_app, idax_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA idax_ledger GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO idax_app, idax_admin;
