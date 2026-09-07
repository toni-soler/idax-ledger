# Changelog

## [0.2.0] - 2026-09-01

- Register the generated Ledger permission catalog through the shared IDAX
  module lifecycle.
- Adopt legacy Ledger permission ownership safely through Flyway migration V3.
- Compose the shared permission and tenant services explicitly in the
  standalone backend.
- Add contract, PostgreSQL semantics and security-composition coverage for
  module permissions.
- Require the public IDAX Core runtime 0.2.0.

## [0.1.0] - 2026-08-24

- Private three-validator XRPL provider with fixed NetworkID and persistent NuDB full history.
- Proof/Anchor v1 using SHA-256 and RFC 8785 JCS.
- Tenant isolation, PostgreSQL RLS, idempotency and audit events.
- Integrated Ledger Explorer and Proof create/verify UI.
- Durable exact-blob submission reconciliation and startup recovery.
- PostgreSQL advisory coordination for multi-instance anchoring writers.
- Anchoring account history binding and rotation runbook.
- Offline consistent backup/restore and disaster-recovery runbooks.
- Operational, security, SBOM and third-party license release preparation.
