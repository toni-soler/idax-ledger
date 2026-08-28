# IDAX Ledger XRPL provider infrastructure

Red XRPL privada reproducible de tres validadores para desarrollo. Ejecutar
`scripts/Initialize-PrivateNetwork.ps1`, `docker compose up -d` y
`scripts/Test-PrivateNetwork.ps1`. Secretos, configuración materializada y
datos se guardan bajo `.runtime`, que no se versiona.

Lifecycle explícito:

```powershell
./scripts/Initialize-PrivateNetwork.ps1 # bootstrap inicial, no forma parte de Start
./scripts/Start-PrivateNetwork.ps1
./scripts/Stop-PrivateNetwork.ps1
./scripts/Restart-PrivateNetwork.ps1
./scripts/Test-PrivateNetworkPersistence.ps1 -ResetNetwork
./scripts/Reset-PrivateNetwork.ps1 -ConfirmNetworkDataLoss # destructivo
```

No es una configuración de producción. Consulte `../docs/SECURITY.md`.
