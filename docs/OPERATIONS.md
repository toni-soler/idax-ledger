# IDAX Ledger operations

## Lifecycle

Recommended startup order: PostgreSQL, XRPL validators, Ledger backend, IDAX
shell/frontend. Gate each step on `pg_isready`, all three XRPL `server_info`
responses reporting `proposing` with aligned validated ledgers, and finally
`/actuator/health/readiness`. Normal shutdown reverses writers first: stop the
backend gracefully, stop validators with 60 seconds grace, then PostgreSQL.
Normal stop never removes volumes, `.runtime`, identities, history or seeds.

From `xrpl` use `Start-PrivateNetwork.ps1`,
`Stop-PrivateNetwork.ps1`, `Restart-PrivateNetwork.ps1` and
`Test-PrivateNetwork.ps1`. `Reset-PrivateNetwork.ps1` is destructive and is not
a lifecycle command.

## Submission recovery

The backend scans `PENDING`, `SIGNED`, `SUBMITTED` and `FAILED_RETRYABLE` at startup and
every configured recovery interval. It acquires a PostgreSQL session advisory
lock derived from the first 64 bits of SHA-256 over
`provider|network|anchoring-account`. The dedicated JDBC connection owns the
lock; process or connection death releases it. Acquisition has a finite timeout.

Recovery always queries XRPL by the persisted transaction hash first. A
validated `tesSUCCESS` is reconciled. A visible unvalidated transaction waits.
If absent and still before `LastLedgerSequence`, the exact persisted signed blob
is resubmitted. It is never re-signed. An expired attempt becomes
`ATTEMPT_EXPIRED`; the logical Proof retains its public/idempotency identity and
requires an explicit operator-approved future attempt.

A completely unsigned `PENDING`/`FAILED_RETRYABLE` row may create its first
signed transaction after recovery. A partially stored or expired signed
attempt is never reconstructed or re-signed.

The lock only covers the writer path. Reads, explorer and verification remain
unblocked. Inspect recoverable rows without selecting the blob:

```sql
select id, proof_id, network_id, anchoring_account, status, transaction_hash,
       sequence_number, last_ledger_sequence, next_retry_at, last_error_code
from idax_ledger.ledger_submission
where status in ('SIGNED','SUBMITTED','FAILED_RETRYABLE','ATTEMPT_EXPIRED');
```

## Anchoring account rotation

1. Generate a new XRPL account offline and fund it on the private network.
2. Install its seed as a new secret; verify file permissions and address match.
3. Stop Ledger writers, configure the new account and seed path, then start one
   canary writer and create/verify a Proof.
4. Start remaining writers. Keep the retired seed in encrypted DR custody.

Every submission stores `anchoring_account`; historical verification checks the
transaction account stored on that submission, not the currently configured
writer. Verification never needs an old seed.

Rotation proof on 24-08-2026: a Proof anchored by replacement account
`rKgUhMBgvMNiqjvEF8iSBPQ2yqEn58BTrN` validated, an older Proof anchored by the
retired account still returned `VALIDATED_MATCH`, and after switching the
writer back the replacement-account Proof also returned `VALIDATED_MATCH`.

Multi-instance proof on 24-08-2026: six concurrent requests split across two
backend JVMs sharing PostgreSQL and the anchoring account all validated with
six unique transaction hashes and six unique XRPL sequences.

## Health and storage

Check node state, peers, `complete_ledgers`, alignment and disk usage of each
validator's `data/db/nudb`, SQLite `data/db`, and logs. Keep
`ledger_history=full` for 0.1.0. Alert conceptually at less than 20%/50 GiB free
(WARNING) and less than 10%/20 GiB (CRITICAL), whichever threshold triggers
first. Future deployments may separate archive/full-history and limited nodes.

Observed 24-08-2026: validator-01 grew 26,112 bytes over an 18 second observation
window while consensus was unavailable. This short sample is not a capacity
forecast; collect daily deltas over at least seven days before sizing production.

## Validator failure behavior

With the current 3-validator UNL, stopping one validator left both survivors in
`proposing` with one peer but the validated ledger did not advance (1228 → 1228
over 18 seconds). Therefore 2/3 is intentionally reported degraded/unavailable
for writes and cannot anchor. Restoring validator-03 returned 3/3 aligned at
ledger 1237 in approximately 10 seconds without reset. Availability requiring
one-node tolerance needs a larger topology/quorum design; it is a declared 0.1.0
limitation.

Backup/restore is defined in `BACKUP_RESTORE.md`; disaster response is in
`DISASTER_RECOVERY.md`.
