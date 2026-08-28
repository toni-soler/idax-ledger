# IDAX Ledger 0.1.0 deployment checklist

- [ ] Approved immutable commit/tag is selected for every repository.
- [ ] Full module validation, backend tests/build, frontend tests/build, i18n
      validation and SBOM generation pass.
- [ ] Flyway validates `idax_ledger`; the target backup has a tested restore.
- [ ] Secrets are external; history, images and frontend bundles scan clean.
- [ ] Validator identities and UNLs pass the configuration guard.
- [ ] Three validators are aligned and progressing on NetworkID `2181844733`.
- [ ] The anchoring address matches its externally mounted seed.
- [ ] One canary Proof validates and returns `VALIDATED_MATCH`.
- [ ] PostgreSQL/RLS tenant isolation and role/permission smoke tests pass.
- [ ] Backup retention, off-host encrypted copy and restore owner are assigned.
- [ ] Disk, ledger age, peers, reconciliation and readiness alerts are active.
- [ ] Rollback stops writers first and preserves all database/XRPL history.
