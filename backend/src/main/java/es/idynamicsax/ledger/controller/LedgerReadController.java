package es.idynamicsax.ledger.controller;

import es.idynamicsax.ledger.provider.*;
import es.idynamicsax.ledger.service.LedgerNetworkView;
import es.idynamicsax.ledger.service.LedgerService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
@PreAuthorize("@permissionService.hasPermission('LEDGER_READ')")
public class LedgerReadController {
    private final LedgerService service;

    public LedgerReadController(LedgerService service) { this.service = service; }

    @GetMapping("/module/status")
    public java.util.Map<String,Object> moduleStatus(){return java.util.Map.of("module","ledger","available",true,"writerScaling","single-instance");}

    @GetMapping("/networks")
    public ResponseEntity<List<LedgerNetworkView>> networks() { return ResponseEntity.ok(service.networks()); }

    @GetMapping("/networks/{networkId}")
    public ResponseEntity<LedgerNetworkView> network(@PathVariable String networkId) {
        return ResponseEntity.ok(service.network(networkId));
    }

    @GetMapping("/networks/{networkId}/status")
    public ResponseEntity<LedgerNetworkStatus> status(@PathVariable String networkId) {
        return ResponseEntity.ok(service.status(networkId));
    }

    @GetMapping("/networks/{networkId}/nodes")
    public ResponseEntity<List<LedgerNodeStatus>> nodes(@PathVariable String networkId) {
        return ResponseEntity.ok(service.nodes(networkId));
    }

    @GetMapping("/networks/{networkId}/ledgers/{ledgerIndex}")
    public ResponseEntity<LedgerView> ledger(@PathVariable String networkId, @PathVariable long ledgerIndex) {
        return ResponseEntity.ok(service.ledger(networkId, ledgerIndex));
    }

    @GetMapping("/networks/{networkId}/transactions/{hash}")
    public ResponseEntity<LedgerTransactionView> transaction(@PathVariable String networkId, @PathVariable String hash) {
        return ResponseEntity.ok(service.transaction(networkId, hash));
    }
}
