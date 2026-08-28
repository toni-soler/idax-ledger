# idax-ledger-backend

Spring Boot 3.4.4 / Java 21 module that depends on IDAX Core and owns only the
generic ledger domain and provider SPI. Authentication, tenant context, RLS,
permissions, audit and common error handling remain platform responsibilities.

Phase 4 provides read-only endpoints under `/api/ledger` for configured
networks, semantic network status, nodes, ledger details and transactions. The
XRPL adapter converts JSON-RPC responses to provider-neutral records; it is not
a generic RPC proxy. Every endpoint requires the generated `LEDGER_READ`
permission.
