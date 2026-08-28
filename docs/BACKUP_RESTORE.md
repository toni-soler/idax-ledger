# Backup and restore

## Scope and consistency

`Backup-Ledger.ps1` produces a standard PostgreSQL custom-format `pg_dump` of
`idax_ledger`, an offline XRPL public archive, an encrypted secrets archive, a
manifest and SHA-256 checksums. Validators are stopped gracefully before files
are copied and restarted in `finally` unless `-LeaveStopped` is supplied.

Public archive: each validator `data` (NuDB plus SQLite database path) and logs.
Encrypted archive: materialized config (contains validator token), validator
identity files, validator secrets and anchoring account/address/seed. Never
publish the encrypted archive or its passphrase together.

```powershell
$env:PGPASSWORD='<database secret>'
$env:IDAX_LEDGER_BACKUP_PASSPHRASE='<strong offline passphrase>'
./scripts/Backup-Ledger.ps1 -BackupRoot D:/idax-ledger-backups
```

Production should replace the environment passphrase with KMS/HSM-backed secret
delivery. OpenSSL uses AES-256-CBC, PBKDF2, salt and 200,000 iterations for the
development implementation.

## Restore

Validate `SHA256SUMS` before restore. Stop writers. On a clean target, install
PostgreSQL 16, Docker and OpenSSL, create the core tenant prerequisites, then:

```powershell
$env:PGPASSWORD='<database secret>'
$env:IDAX_LEDGER_BACKUP_PASSPHRASE='<offline passphrase>'
./scripts/Restore-Ledger.ps1 -BackupPath D:/idax-ledger-backups/idax-ledger-... `
  -RuntimePath E:/idax-runtime -ConfirmRestore
```

The script moves an existing runtime aside instead of deleting it, decrypts
identities without regenerating them, and restores the schema with `pg_restore`.
Mount the chosen runtime path in Compose, start validators, verify NetworkID
`2181844733`, identities, consensus and historical ledgers, then start writers.
Paths are parameters and are not part of network identity.
