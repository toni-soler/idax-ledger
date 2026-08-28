# idax-ledger-main

Composición de desarrollo. Por defecto incluye sólo los tres validadores XRPL.
Los perfiles `standalone` y `application` añaden PostgreSQL local y backend; no
duplican el frontend principal IDAX, que consume el artefacto modular.

Copiar `.env.example` a `.env`, cambiar la contraseña y ejecutar:

```powershell
docker compose --profile standalone --profile application up -d --build
```
