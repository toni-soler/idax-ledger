# Disaster recovery validation

The release gate is a destructive test performed only after a backup is present,
checksummed and readable:

1. Create three unique Proofs and wait for `VALIDATED`; record public ID,
   transaction hash, ledger index/hash and verification result.
2. Run the offline backup and verify all SHA-256 entries.
3. Stop backend and validators. Move, never initially delete, the original
   runtime and restore into a different path.
4. Restore PostgreSQL `idax_ledger`, identities, configs and secrets.
5. Start three validators; require the same NetworkID and validator public keys,
   aligned progressing ledgers and readable recorded historical ledgers.
6. Start backend and verify all three Proofs produce
   `MATCH + VALIDATED_MATCH` with identical recorded identifiers/hashes.

Do not mark Phase 7 PASS without attaching the non-secret result JSON. If any
historical tuple changes or cannot be read, retain both runtimes and declare the
test failed. The anchoring and validator seeds never belong in evidence.

## Executed 0.1.0 evidence — 24-08-2026

Backup `idax-ledger-20260824-153723` was restored into the distinct runtime
`idax-ledger-restored-runtime`; PostgreSQL schema `idax_ledger` was restored
from the same set. The restore validated the manifest NetworkID and SHA-256
checksums before mutation. Three validators reached consensus and these tuples
remained identical and returned `VALIDATED_MATCH`:

| Proof ID | Transaction hash | Ledger hash |
|---|---|---|
| `acf19e75-9869-4426-8919-da9cdc2fb8f1` | `9F3B6C44BD8609A09C51580DA78A7797E36AB42C7B89D6982179DA8B134997BF` | `676893BF5C47B31A0B8E761E3E4F0C6DABF2A71D52AF71974ABD6E5ED8493D30` |
| `0543d522-4fd0-4346-9660-b1613a4afa4f` | `2963CCED9504A45531DA306D57D2CA364D4A615F954318AE62869D9C2F2DF1C9` | `EA2BEB9E9B367832E622930C7A16BD5F37FCB84D5C8CE3658B0F5EF10123B764` |
| `97d8b10e-9ab1-4b5d-995d-b8c91ab3af5a` | `80AAE235294208595D5B54C3CDC92AE06A8264331E598B6313B13E31D1ADB91D` | `7A2CDE6E0D49C0F3D8C4C33C2599B2C41E49EC3E9949A15AB86E953BC37AABEE` |
