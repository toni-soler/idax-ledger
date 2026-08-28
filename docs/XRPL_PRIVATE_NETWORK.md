# XRPL private network

Red de desarrollo de tres validadores, cada uno con token, base de datos y
configuración persistentes independientes. Usa `network_id=2181844733`
(`0x820C4EFD`); toda transacción debe llevar `NetworkID: 2181844733`.

El ID se generó el 2026-08-24 tomando cuatro bytes de
`System.Security.Cryptography.RandomNumberGenerator` (`FD 4E 0C 82`, orden
little-endian). Se descartaría cualquier resultado menor que 1025 o presente
en la lista pública conocida; el valor resultante no coincide con `0`, `1`,
`2`, `21336`, `21337`, `21338` ni `31338`. Es la constante oficial de IDAX
Private XRPL y no debe cambiarse sin un nuevo génesis explícito.

Desde `xrpl`, el bootstrap y el arranque son operaciones separadas:

```powershell
./scripts/Initialize-PrivateNetwork.ps1
./scripts/Start-PrivateNetwork.ps1
```

El inicializador genera secretos en `.runtime`, nunca versionados. El test exige
tres respuestas, estado `proposing`, al menos dos peers, ledger validado y
progresión entre dos observaciones. `docker compose down` conserva identidades y
datos; `down -v` elimina datos, pero no secretos/config generados en `.runtime`.
Compose concede 60 segundos a cada proceso para cerrar limpiamente su base de
datos antes de forzar la parada.

## Política y mapa de persistencia

La red usa NuDB con `fast_load=1`, `ledger_history=full` y sin
`online_delete`/`advisory_delete`. Es una política deliberada para el volumen
mínimo de desarrollo. En producción debe definirse retención y capacidad antes
de adoptarla: full-history público tiene un coste radicalmente distinto.

| Componente | Ruta contenedor | Persistencia real | Sensible |
|---|---|---|---|
| Clave maestra del validator | no se monta en el proceso; fuente en `.runtime/validator-NN/secrets/validator-keys.json` | bind mount sólo durante `validator-keys`; host | Sí |
| Validator token | `/etc/xrpld/xrpld.cfg` materializado desde `/config/xrpld.cfg` | `.runtime/validator-NN/config/xrpld.cfg` | Sí |
| NodeDB NuDB | `/var/lib/xrpld/db/nudb/{nudb.dat,nudb.key,nudb.log}` | `.runtime/validator-NN/data/db/nudb` | No* |
| SQLite/bookkeeping | `/var/lib/xrpld/db/*.db` | `.runtime/validator-NN/data/db` | No* |
| Identidad P2P del nodo | `/var/lib/xrpld/db/wallet.db` | mismo bind mount de datos | Sí |
| Config/UNL | `/config`, copiado a `/etc/xrpld` por el entrypoint | `.runtime/validator-NN/config` | token: Sí |
| Logs | `/var/log/xrpld/debug.log` | `.runtime/validator-NN/log` | Operacional |

El mount de `/var/lib/xrpld` cubre tanto `node_db` como `database_path`.
NodeDB contiene los objetos que forman el estado y los ledgers históricos;
SQLite mantiene índices/bookkeeping de ledgers y transacciones. `docker stop`,
`start`, `compose down` y la recreación del contenedor no eliminan esos bind
mounts. `Reset-PrivateNetwork.ps1` es la única operación del proyecto que los
borra, exige `-ConfirmNetworkDataLoss` y elimina también identidades.

Referencias: configuración de `xrpld 3.3.0`, guía oficial de full history y
guía oficial de migración/backup:

- https://github.com/XRPLF/rippled/blob/3.3.0/cfg/xrpld-example.cfg
- https://xrpl.org/docs/infrastructure/configuration/data-retention/configure-full-history
- https://xrpl.org/docs/infrastructure/installation/migrate-to-xrpld

## Incidencia original y causa raíz

**ROOT CAUSE CONFIRMED.** Los `data-stalled-*` contienen NuDB y SQLite íntegros
(`PRAGMA integrity_check=ok`) y ledgers 4–37; no hubo pérdida física. El defecto
era de bootstrap: `fast_load` no estaba configurado y su valor por defecto en
3.3.0 es `0`. Tras un reinicio simultáneo, los tres peers esperaban obtener un
ledger inicial de la red, pero ninguno cargaba primero el último ledger local.
Quedaban conectados con `complete_ledgers=empty`, aunque el almacenamiento
persistía. `fast_load=1` carga el último ledger persistido antes de sincronizar.

La evidencia automatizada de 2026-08-24 seleccionó ledger `11`, hash
`61F2A090930599E1F6AECA2D7DFD7BF3DB4874E854DABA91E8E7E0CE6F4B7D3F`,
y lo recuperó sin cambios tras dos reinicios y recreación forzada. El resultado
completo queda temporalmente en `.runtime/persistence-test-result.json`.

`complete_ledgers` describe el rango marcado completo en la sesión activa. Con
`fast_load`, tras reiniciar puede comenzar en el ledger cargado (por ejemplo
`15`, después `15-17`) aunque un ledger anterior sea recuperable directamente
desde NuDB/SQLite y responda `validated=true`. El test exige ambas cosas: rango
activo no vacío que incluya el ledger validado actual, y consulta directa del
ledger histórico con hash idéntico. Un simple estado `proposing` no basta.

## Lifecycle y prueba de resiliencia

- `Start-PrivateNetwork.ps1` nunca genera claves ni configuración: falla si no
  hubo bootstrap explícito.
- `Stop-PrivateNetwork.ps1` envía parada graceful con 60 segundos.
- `Restart-PrivateNetwork.ps1` compone stop/start sin tocar datos.
- `Reset-PrivateNetwork.ps1 -ConfirmNetworkDataLoss` destruye deliberadamente
  toda la red local.
- `Test-PrivateNetworkPersistence.ps1 -ResetNetwork` crea una red limpia,
  captura identidades/NetworkID/ledger/hash/`complete_ledgers`, hace parada
  prolongada, dos reinicios y recreación, y falla si el ledger histórico o su
  hash no sobreviven.

Puertos host RPC: 5006, 5007 y 5008. Peer: 9001, 9002, 9003. WebSocket: 6006,
6007, 6008. Sólo son valores de desarrollo y se externalizan en `.env`.

Esta topología sigue la guía oficial de XRPL para desarrollo. No es una red de
producción: tres validadores bajo un solo operador/host no proporcionan
descentralización ni tolerancia operacional suficiente.
