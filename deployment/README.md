# idax-ledger-main

Composición Linux de desarrollo. Por defecto inicia PostgreSQL, aplica las
migraciones públicas de IDAX Core y construye/inicia el backend. La red privada
XRPL es opcional mediante el perfil `xrpl`; requiere inicializar antes sus
ficheros y secretos. El frontend es una extensión del host IDAX, no una SPA
independiente.

Copiar `.env.example` a `.env`, cambiar la contraseña y ejecutar:

```shell
cp .env.example .env
# Cambia IDAX_LEDGER_DB_PASSWORD en .env
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8094/actuator/health/readiness
```

`keys/dev-public.pem` sólo sirve para arrancar el perfil local y no tiene una
clave privada distribuida. Sustitúyelo por la clave pública del emisor JWT real
fuera de desarrollo.

Para levantar también XRPL, ejecuta primero el bootstrap descrito en
`../xrpl/README.md` y después `docker compose --profile xrpl up -d --build`.
