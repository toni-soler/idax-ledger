# Development

Requisitos: Java 21, Maven, Node/npm, Python con PyYAML y Docker Desktop con
contenedores Linux amd64.

1. Instalar `idax-core`: `mvnw.cmd -pl idax-core -am -DskipTests install` desde
   `idax/idax-platform`.
2. Ejecutar el generador Ledger desde `backend`.
3. Ejecutar `mvn test` y `mvn package -DskipTests`.
4. Ejecutar `npm install`, `npm test`, `npm run i18n:validate` y
   `npm run build` en frontend.
5. Inicializar y levantar la red siguiendo `XRPL_PRIVATE_NETWORK.md`.
   Después ejecutar `./scripts/Initialize-AnchoringAccount.ps1`; copiar sólo la
   address a `IDAX_LEDGER_ANCHOR_ACCOUNT` y configurar
   `IDAX_LEDGER_ANCHOR_SEED_FILE` con la ruta al fichero secreto.
6. Ejecutar `validate-idax-module <workspace> --full` desde DevKit.

Para validar Fase 4 contra la red local, levantar primero XRPL y configurar
`IDAX_LEDGER_XRPL_NODE_01_URL`…`03_URL` si no se usan los puertos por defecto.
`GET /actuator/health` incluye el componente `ledgerNetworks`; estará `UP` sólo
si todos los nodos responden, tienen peers, el ledger es reciente y sus índices
están alineados.

La validación obligatoria de persistencia se ejecuta desde `xrpl`:

```powershell
./scripts/Test-PrivateNetworkPersistence.ps1 -ResetNetwork -LongStopSeconds 30
```

Es destructiva para la red local activa. Conserva evidencia bajo `.runtime` y
comprueba dos reinicios, recreación de contenedores, identidades, NetworkID,
progresión, `complete_ledgers` y hash histórico.

Los endpoints `/api/ledger/**` aceptan el mismo bearer JWT que IDAX. En modo
`LOCAL`, el backend valida con la clave pública en
`src/main/resources/keys/public.pem`; es material público y debe mantenerse
sincronizado con la clave emisora de IDAX. Puede sustituirse sin reconstruir con
`IDAX_LEDGER_AUTH_PUBLIC_KEY_LOCATION=file:/ruta/public.pem`. En modo
`KEYCLOAK`/`DUAL` se reutiliza `spring.security.oauth2.resourceserver.jwt.issuer-uri`.

No editar output generado como solución final. No guardar claves privadas en Git.

Prueba real Proof/Anchor: habilitar `IDAX_LEDGER_RUN_E2E=true` y ejecutar
`LedgerProofE2ETest`. Conserva evidencia no secreta en
`target/phase5b-e2e-result.json`. Reiniciar los tres nodos, esperar consenso y
ejecutar `LedgerProofRestartVerificationE2ETest` con
`IDAX_LEDGER_RUN_RESTART_E2E=true`; debe recuperar la transacción/ledger antiguos
y devolver `MATCH + VALIDATED_MATCH`.

## Fase 6 — Frontend integrado y explorer

El shell principal carga la extensión nativa desde `/ledger/extensions/index.js`
y enruta `/ledger/*` sin iframe, login ni layout paralelo. El proxy local separa
`/api/ledger/**` (backend Ledger en `8094`) del backend core (`8080`). La extensión
reutiliza el JWT, tenant activo, permisos, `fetchWithAuth`, React Router e i18n
del shell.

Pantallas disponibles: overview, redes, nodos, ledgers, transacciones y pruebas.
Las pruebas admiten JSON canónico JCS o hash SHA-256 precalculado, muestran la
clave de idempotencia, el estado de anclaje, verificación independiente de
contenido/ledger y enlaces Proof ↔ Transaction → Ledger.

Validación local realizada el 24-08-2026 con el tenant `TEST-LEDGER`: prueba
`0390fac4-8114-4945-a768-a413bd24a194`, transacción
`E059650E9682CEE1F50B6D5CD030330C54B5D32F4175520DF34C8A9FB5B9A235` y ledger
`1107`. El resultado fue `MATCH + VALIDATED_MATCH`. Esta evidencia es pública;
el seed permaneció únicamente en `.runtime`.
