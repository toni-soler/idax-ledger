# IDAX Ledger 0.1.0 release notes

Initial provider-neutral Proof/Anchor platform with a private three-validator
XRPL provider, NetworkID enforcement, RFC 8785 JCS/SHA-256 Proof v1, tenant RLS,
idempotency, Ledger Explorer, integrated Proof UI and independent verification.
Operational hardening adds durable submission reconciliation, exact-blob retry,
expired-attempt safety, PostgreSQL advisory writer coordination, account-history
binding and offline encrypted backup/restore tooling.

Deployment requires PostgreSQL 16, Java 21, Docker Linux amd64 support, three
XRPL validator identities, a separately funded anchoring account and protected
secret storage. Back up PostgreSQL, all XRPL database paths and encrypted
identity/seed material together using the documented offline procedure.

Known limitations: one active anchoring account at a time; the current 3-node
UNL does not progress with one validator offline; full history grows without
bound; topology changes are manual; no Byzantine test suite or public verifier.

Not included: Mainnet crypto custody, financial/payment functionality, wallets,
tokens, Bitcoin, Litecoin, The Market, osTRIS or Mutual Credit.
