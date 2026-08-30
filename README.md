# IDAX Ledger

IDAX Ledger provides tenant-aware proof anchoring for IDAX modules. XRPL is the
first provider, while the public API and persisted proof model remain
provider-neutral.

This public repository is a sanitized source snapshot. It intentionally has a
history independent from the private development repositories.

## Repository layout

- `backend/`: Java 21 and Spring Boot proof service.
- `frontend/`: integrated IDAX frontend extension.
- `xrpl/`: private XRPL network bootstrap and operations.
- `deployment/`: local Docker Compose composition.
- `docs/`: architecture, operations and security documentation.

For the shortest deployment and day-two operations path, see
[`docs/QUICKSTART.md`](docs/QUICKSTART.md).

## IDAX runtime dependency

The backend requires the closed-source Maven artifact
`es.idynamicsax.idax:idax-core`. Core source, source JARs, generators and the
Dynamics AX-specific `idax-legacy` module are not part of this repository.
Database prerequisites and artifact-resolution guidance live in the companion
[`idax-core-runtime`](https://github.com/toni-soler/idax-core-runtime)
repository. Maven downloads the binary anonymously from its public repository;
no GitHub token or manual installation is required.

For an unpublished local Core build, override the version explicitly:

```shell
cd backend
mvn test -Didax.version=0.0.1-SNAPSHOT
```

## Build

```shell
cd backend
mvn test

cd ../frontend
npm ci
npm test
npm run build
```

Never commit XRPL validator keys, validator tokens, anchoring seeds, JWT private
keys, database passwords or `.env` files. See `docs/SECURITY.md` before running
the deployment composition.

## License

The source in this repository is licensed under Apache License 2.0. The
separately distributed `idax-core` binary has its own license and is not covered
by this repository's Apache license.
