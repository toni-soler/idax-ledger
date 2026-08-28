# Proof / Anchor v1 design (Phase 5A)

Estado: diseño 5A implementado por Fase 5B.

## Límites

`LedgerProof` es provider-neutral. Contiene identidad, tenant, digest y estado
funcional. `LedgerSubmission` representa un intento contra un provider/red y
puede contener campos técnicos de seguimiento. `XrplLedgerProvider` es el único
componente que conoce `AccountSet`, `Memos`, `Sequence`, `NetworkID`, firma y
submission XRPL.

PostgreSQL conserva metadata completa; XRPL sólo recibe formato v1, un ID opaco
y el digest. Nunca se escriben tenant, `externalId`, tipo de documento,
metadata, PII ni contenido original en el ledger.

## Entradas y canonicalización

La API futura aceptará exactamente uno de estos modos:

1. Hash precalculado: `hashAlgorithm=SHA-256` y `hash` hexadecimal de 64
   caracteres. El perfil obligatorio es `RAW-BYTES-SHA256-V1` o
   `EXTERNAL:<perfil-productor>`; el segundo no es reproducible por Ledger sin
   conocer ese perfil.
2. Payload JSON: el backend valida I-JSON, aplica **RFC 8785 JCS** incluyendo
   errata verificadas, obtiene bytes UTF-8 y calcula SHA-256.

Perfil persistido para JSON: `JCS-RFC8785-UTF8-V1`. Se rechazan claves
duplicadas, números no representables por IEEE-754, NaN/Infinity, surrogates
Unicode inválidos y `-0`. No se usa la serialización accidental de Jackson.
Arrays conservan orden; las propiedades se ordenan recursivamente según JCS.

Referencia normativa: https://www.rfc-editor.org/rfc/rfc8785 y
https://www.rfc-editor.org/errata/rfc8785.

## Modelo PostgreSQL propuesto

La primera migración de Fase 5B creará únicamente dos tablas:

### `idax_ledger.ledger_proof`

- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- `public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()` (opaco; todavía
  no habilita verificación pública)
- `tenant_id UUID NOT NULL REFERENCES idax_core.tenant(tenant_id)`
- `external_id VARCHAR(255) NOT NULL`
- `proof_type VARCHAR(100) NOT NULL`
- `hash_algorithm VARCHAR(20) NOT NULL` (`SHA-256` en v1)
- `content_hash VARCHAR(64) NOT NULL`
- `canonicalization_profile VARCHAR(100) NOT NULL`
- `format_version SMALLINT NOT NULL DEFAULT 1`
- `metadata_json JSONB NOT NULL DEFAULT '{}'`
- `idempotency_key VARCHAR(255) NOT NULL`
- `status VARCHAR(32) NOT NULL`
- auditoría IDAX: `created_at`, `updated_at`, `created_by`, `updated_by`,
  `version`
- unique `(tenant_id, idempotency_key)`
- unique semántica `(tenant_id, external_id, proof_type, content_hash)`

La misma idempotency key con representación normalizada diferente devuelve
`409 IDEMPOTENCY_CONFLICT`; con la misma representación devuelve el Proof ya
existente y nunca crea otra transacción.

### `idax_ledger.ledger_submission`

- `id`, `tenant_id`, `proof_id`
- `network_id` lógico (`private-xrpl`), `provider_type`
- `status`, `attempt_count`, `next_retry_at`, `last_error_code/detail`
- `transaction_hash`, `signed_transaction_blob`, `ledger_index`, `ledger_hash`
- `submitted_at`, `validated_at` y campos estándar de auditoría/versionado
- unique `(proof_id, network_id)` y `(network_id, transaction_hash)` cuando no
  sea null

El blob firmado no contiene la clave privada y permite reenviar exactamente la
misma transacción tras un fallo ambiguo. Nunca se vuelve a firmar ciegamente una
segunda transacción para la misma submission.

Ambas tablas habilitan RLS y políticas `idax_admin`/`idax_app` idénticas a los
módulos recientes, usando `app.tenant_id`. No se persisten networks/nodes: su
fuente autoritativa continúa siendo configuración.

## Estados

`PENDING -> SIGNED -> SUBMITTED -> VALIDATED`.

Fallos desde `PENDING`, `SIGNED` o `SUBMITTED` pasan a `FAILED_RETRYABLE` o
`FAILED_PERMANENT`. `SIGNED` está justificado porque separa “hash de transacción
estable ya conocido” de “resultado de submit desconocido”. Un retry desde
`FAILED_RETRYABLE` reutiliza el blob/hash persistido. Sólo `VALIDATED` es final
y exitoso; `SUBMITTED` no prueba inclusión.

## Formato on-ledger v1

`AccountSet` sin cambios de configuración, con un Memo. La documentación XRPL
confirma que un AccountSet sin opciones sólo consume la tarifa. El Memo usa:

- `MemoType`: UTF-8 `urn:idax:ledger:proof:v1`, hex uppercase.
- `MemoFormat`: UTF-8 `application/jcs+json`, hex uppercase.
- `MemoData`: UTF-8 del siguiente JSON JCS, hex uppercase:

```json
{"d":"<64 lowercase hex>","f":"IDAX_LEDGER_PROOF","i":"<public UUID lowercase>","v":1}
```

Los nombres cortos son parte inmutable del wire format. `d` es SHA-256; `i` es
opaco; `f` evita interpretar otro memo como proof; `v` gobierna evolución. No
se incorporan `externalId`, tenant ni metadata. El payload queda ampliamente
por debajo del límite binario de 1 KiB para Memos.

La transacción incluye `Account`, `Fee`, `Sequence`, `LastLedgerSequence`,
`NetworkID=2181844733`, `Flags=2147483648` (`tfFullyCanonicalSig`, constante
semántica en código) y firma.
`NetworkID` es obligatorio para
redes con ID >=1025 y protege frente a replay entre cadenas. El adaptador debe
rechaza NetworkID ausente o distinto del reportado por el servidor. El valor no
está fijado en la lógica genérica, sino en la configuración de la red.

V1 serializa por cuenta la preparación/envío mediante lock de proceso. Así no
firma dos operaciones con el mismo `Sequence`; cinco operaciones concurrentes
se validan E2E. `LastLedgerSequence` es ledger validado actual +20. Cada estado
se confirma en una transacción PostgreSQL separada: primero blob/hash y
`SIGNED`, después se envía exactamente ese blob, se confirma `SUBMITTED`, y
sólo tras inclusión `tesSUCCESS` en ledger validado se confirma `VALIDATED`.
Ante resultado ambiguo no se genera automáticamente otra firma; la recuperación
consulta el hash y sólo puede reenviar el blob persistido mientras sea vigente.

La clasificación es conservadora: red, timeout o nodo temporal son retryable;
NetworkID/firma/transacción rechazada/saldo insuficiente son permanentes. Una
operación expirada exige intervención y nueva política explícita, no refirma
ciega.

Referencias: https://xrpl.org/docs/references/protocol/transactions/common-fields
y https://xrpl.org/docs/references/protocol/transactions/types/accountset.

## Cuenta de anchoring y secreto

Se crea una cuenta de aplicación independiente; nunca se usa una clave de
validator. Para desarrollo, `Initialize-AnchoringAccount.ps1` genera/fondea la cuenta
y guarda el seed sólo en `.runtime/anchoring/secrets`, ignorado por Git. El
backend recibe el secreto por Docker secret o variable apuntando a fichero, no
como valor de `application.yml`, API, frontend o log. Producción debe usar un
secret manager y permitir rotación controlada.

## Verificación

La respuesta no será booleana:

- `integrity`: `MATCH`, `MISMATCH`, `NOT_EVALUATED`, con digest calculado y
  perfil usado.
- `ledger`: `VALIDATED_MATCH`, `NOT_FOUND`, `NOT_VALIDATED`,
  `ANCHOR_MISMATCH`, `PROVIDER_UNAVAILABLE`, con transaction/ledger refs.

La consulta autenticada siempre filtra por tenant y RLS. Una futura
verificación pública sólo podrá usar `public_id`/token opaco y una respuesta
reducida, sin metadata ni IDs internos; no se implementa ahora.

## API y permisos

- `POST /api/ledger/proofs` — `LEDGER_PROOF_CREATE`
- `GET /api/ledger/proofs/{id}` — `LEDGER_READ`
- `POST /api/ledger/proofs/{id}/verify` — `LEDGER_PROOF_VERIFY`

Los nombres siguen `RESOURCE_ACTION`, como `LEDGER_READ`, y están incorporados
al catálogo generator-driven. Creación, submission, validación y verificación
emiten auditoría funcional sin payload, seed ni blob firmado.

## Operación y recuperación

`POST /api/ledger/proofs` exige `Idempotency-Key`. Un retry idéntico devuelve
proof, public ID, submission y transaction hash originales; uno conflictivo
devuelve `409 IDEMPOTENCY_CONFLICT`. `GET` recupera el estado y `POST .../verify`
recalcula opcionalmente integridad y consulta de nuevo transacción, ledger, hash
y Memo en XRPL. Un `FAILED_RETRYABLE` se inspecciona por transaction hash antes
de reenviar su blob; no hay worker distribuido en esta fase.

## Criterio E2E de Fase 5B

La implementación no se considera terminada hasta crear un proof `VALIDATED`,
persistir transaction/ledger hash, reiniciar los tres nodos, recuperar la
transacción y el ledger anteriores y obtener `integrity=MATCH` y
`ledger=VALIDATED_MATCH`.
