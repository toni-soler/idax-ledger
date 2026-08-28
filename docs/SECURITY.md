# Security

## Clasificación de secretos

- Claves maestras/token de validador: un juego independiente por nodo, generado
  fuera de imagen; directorio `.runtime/secrets` ignorado y permisos mínimos.
- Seed/cuentas de transacción: nunca frontend ni configuración versionada;
  secret manager en producción y cuentas técnicas de privilegio mínimo. El
  backend sólo recibe una ruta a Docker secret/fichero externo. El desarrollo
  usa `.runtime/anchoring/secrets/anchor.seed`, ignorado, y el bootstrap nunca
  imprime el seed.
- Credenciales PostgreSQL/API: Docker secrets o secret manager; `.env` sólo en
  desarrollo y siempre ignorado.
- Identidades públicas de validador sí pueden observarse; claves secretas y
  tokens nunca se devuelven por API ni aparecen en logs.

El bootstrap incluido es exclusivamente local. Producción requiere generación
offline, backup cifrado, rotación/revocación, separación de hosts, TLS/mTLS,
firewall, validadores detrás de stock servers y revisión de la UNL. Las APIs
administrativas heredan autenticación/permisos IDAX y no exponen JSON-RPC admin.

Ante una filtración: detener el nodo afectado, revocar su clave, retirar su
identidad de la UNL, generar una identidad nueva offline y auditar logs/config.

La cuenta de anchoring se separa de validators y usuarios. Rotarla exige crear y
fondear otra cuenta, desplegar su secret/address juntos, drenar submissions
firmadas de la cuenta anterior y conservar la address antigua para verificar
anchors históricos. Audit/logs excluyen seed, signed blob y contenido sensible.
Los XRP privados de fondeo no tienen valor, relación ni intercambiabilidad con
XRP Mainnet.
