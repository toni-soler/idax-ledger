# idax-ledger-frontend

Extensión modular para el shell React de IDAX. No incluye autenticación,
layout, navegación paralela ni iframe; declara rutas/pantallas para que el
frontend principal aplique sus componentes, permisos, HTTP e i18n comunes.

La extensión se empaqueta como IIFE en `dist/extensions/index.js` y registra
su componente en `window.__IDAX_MODULE_EXTENSIONS__`. El shell inyecta un SDK
acotado con React, router, i18n, autenticación y HTTP tenant-aware. Para validar:

```powershell
npm test
npm run i18n:validate
npm run build
```

Rutas: `/ledger`, `/ledger/networks`, `/ledger/nodes`, `/ledger/ledgers/:index`,
`/ledger/transactions/:hash`, `/ledger/proofs`, `/ledger/proofs/new` y
`/ledger/proofs/:id`.
