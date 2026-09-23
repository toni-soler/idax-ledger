# Changelog

## [0.4.0] - 2026-09-23

- Require IDAX Core Runtime 0.4.0.
- Preserve the existing Ledger API, proof formats and frontend bundle.
- Confirm that generated metadata is bundled at build time and therefore does
  not need the runtime metadata-fetch cache introduced for large modules.

## [0.3.0] - 2026-09-09

- Require IDAX Core Runtime 0.3.0.
- Add the integrated public frontend extension to the IDAX Shell local stack.
- Support authenticated module routing and local Maven candidate validation.
- Preserve the existing proof canonicalization and anchor formats.

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
