# IDAX Ledger 0.2.0 release notes

IDAX Ledger 0.2.0 migrates permission registration to the shared module
permission-catalog lifecycle introduced by IDAX Core 0.2.0.

The release adds a guarded Flyway adoption migration for installations that
already contain the three legacy Ledger permissions. It also narrows and tests
the standalone Spring composition required for permission, audit, tenant and
data-area semantics.

Upgrade order:

1. Back up PostgreSQL and, when enabled, XRPL runtime data and secrets.
2. Update to IDAX Core runtime 0.2.0 and apply its migrations.
3. Deploy IDAX Ledger 0.2.0; Flyway applies Ledger migration V3.
4. Verify readiness, permission registration and authenticated proof access.

No Ledger proof, anchor or canonicalization format changes in this release.
