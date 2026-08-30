# IDAX Ledger deployment and operations quickstart

This document is the public-safe operational source for the standalone IDAX
Ledger distribution. Commands target Linux with Docker Engine and Docker
Compose v2.

## Prerequisites

- Git, `curl`, Docker Engine and `docker compose`.
- At least 4 GB of free RAM for PostgreSQL, the backend and optional XRPL
  validators.
- Ports `8094` (backend) and, when XRPL is enabled, its ports must be free.

## Start the basic stack

```shell
git clone https://github.com/toni-soler/idax-ledger.git
cd idax-ledger/deployment
cp .env.example .env
```

Edit `.env` and replace `IDAX_LEDGER_DB_PASSWORD=change-me`. Do not commit the
file. Then start PostgreSQL, the public IDAX Core migrations and the backend:

```shell
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8094/actuator/health/readiness
```

The backend is healthy when the last command returns a response whose status is
`UP`. The frontend in this repository is an IDAX host extension, not a separate
standalone web application.

## Authentication

`deployment/keys/dev-public.pem` is a development-only public key. It has no
matching private key in the repository. To make authenticated requests, mount
the public key of your real JWT issuer at the same container path or adapt the
Compose volume. Never copy a JWT private key into this repository.

## Optional private XRPL network

The basic stack starts with private-ledger submission disabled. To run the
three-validator development network, follow `xrpl/README.md` to generate local
validator material and the anchoring account, set the corresponding `.env`
values, and then run:

```shell
docker compose --profile xrpl up -d --build
docker compose --profile xrpl ps
```

Validator keys, validator tokens and the anchoring seed are runtime secrets and
must never be committed. See `docs/XRPL_PRIVATE_NETWORK.md` and
`docs/OPERATIONS.md` for the complete operating model.

## Routine operations

```shell
# Follow application logs
docker compose logs -f --tail=200 backend

# Inspect every service
docker compose ps -a

# Restart only the backend
docker compose restart backend

# Stop containers without deleting data
docker compose down

# Start again using the existing database volume
docker compose up -d
```

Back up the PostgreSQL volume and, when XRPL is enabled, the ledger data and
anchoring secrets before upgrades. The XRPL backup and recovery scripts live in
`xrpl/scripts/`; detailed procedures are in `docs/BACKUP_RESTORE.md` and
`docs/DISASTER_RECOVERY.md`.

## Update

```shell
cd idax-ledger
git pull --ff-only
cd deployment
docker compose build --pull
docker compose up -d
docker compose ps
curl --fail http://localhost:8094/actuator/health/readiness
```

The `core-migrations` one-shot service applies pending public IDAX Core Flyway
migrations before the backend starts. Do not skip backups or manually edit an
already-applied migration.

## Stop and remove development data

`docker compose down` preserves data. The following command permanently removes
the Compose volumes and must only be used when deliberately resetting a
development installation:

```shell
docker compose down --volumes
```

## Troubleshooting

```shell
docker compose config
docker compose ps -a
docker compose logs --tail=300 postgres core-migrations backend
```

- If PostgreSQL is unhealthy, verify the password and volume permissions.
- If `core-migrations` fails, inspect its logs before restarting the backend.
- An HTTP `401` or `403` usually means the mounted JWT public key, issuer or
  claims do not match the token.
- For XRPL failures, verify that all validators are healthy and that the
  anchoring account and seed file were initialized as documented.

This Compose setup is a development/evaluation baseline. Production requires
external secret management, TLS, monitored backups, resource limits, hardened
networking and an independently operated JWT issuer.
