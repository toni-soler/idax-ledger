# Discovery

## Ecosistema verificado

- Contrato obligatorio: IDAX New Module Contract v1 y `gen-idax-module`.
- Backend base: Java 21, Spring Boot 3.4.4, Spring Data/JPA, Flyway y PostgreSQL.
- Frontend base: React 18.3.1, JavaScript y extensiones integradas en el shell;
  no iframe.
- `idax-core` ya aporta JWT/autenticación, usuarios, `TenantContext`, resolución
  de usuario, permisos, auditoría, aplicación de contexto PostgreSQL y RLS.
- Los módulos recientes son repos independientes bajo una raíz workspace; la
  raíz ignora hijos y contiene manifiesto y `.code-workspace`.
- RealEstate es la referencia contract-v1; FactuFlow es la referencia histórica
  más rica para generación declarativa.
- El frontend principal ya posee layout, navegación, interceptor HTTP, errores,
  componentes CRUD/filtros y 12 locales. Ledger debe conectarse a esos puntos,
  no copiarlos.
- Flyway y RLS deben vivir en el backend del módulo con esquema `idax_ledger`;
  no se ha creado aún modelo persistente porque la Fase 1 exige cerrarlo antes.
- Deployment de módulos recientes usa un repo `main` con Compose y no duplica
  infraestructura global salvo un perfil de desarrollo autocontenido.

## XRPL verificado

- La guía oficial mantiene un ejemplo de red privada Docker con tres
  validadores, claves distintas, UNL compartida y estado esperado `proposing`.
- `xrpld` es ISC; la imagen empleada por la guía es
  `xrpllabsofficial/xrpld`. Se fija versión, nunca `latest`.
- `network_id >= 1025` obliga a incluir `NetworkID` en transacciones y reduce el
  riesgo de replay/confusión con redes públicas. IDAX Private XRPL fija el
  UInt32 pseudoaleatorio `2181844733` (`0x820C4EFD`).
- Una red de tres validadores es adecuada para desarrollo, no una topología de
  producción tolerante a fallos ni un sustituto de gestión profesional de UNL.

## Decisiones de discovery

La raíz `idax-ledger` sustituye al nombre propuesto `idax-ledger-workspace` por
exigencia contractual. Se conservan backend, frontend, main, XRPL y doc. No se
usa el prototipo `market`. No se introducen Kafka, Redis, Kubernetes, CQRS,
GraphQL ni una segunda identidad/tenant.
