package es.idynamicsax.ledger.controller;
import es.idynamicsax.ledger.proof.*; import java.util.UUID; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ledger/proofs")
public class LedgerProofController{
 private final LedgerProofService service; public LedgerProofController(LedgerProofService service){this.service=service;}
 @PostMapping @PreAuthorize("@permissionService.hasPermission('LEDGER_PROOF_CREATE')") public ResponseEntity<ProofViews.Proof> create(@RequestHeader("Idempotency-Key")String key,@RequestBody String body){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(key,body));}
 @GetMapping @PreAuthorize("@permissionService.hasPermission('LEDGER_READ')") public java.util.List<ProofViews.Proof> list(){return service.list();}
 @GetMapping("/summary") @PreAuthorize("@permissionService.hasPermission('LEDGER_READ')") public ProofViews.Summary summary(){return service.summary();}
 @GetMapping("/public/{publicId}") @PreAuthorize("@permissionService.hasPermission('LEDGER_READ')") public ProofViews.Proof getByPublicId(@PathVariable UUID publicId){return service.getByPublicId(publicId);}
 @GetMapping("/{id}") @PreAuthorize("@permissionService.hasPermission('LEDGER_READ')") public ProofViews.Proof get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/verify") @PreAuthorize("@permissionService.hasPermission('LEDGER_PROOF_VERIFY')") public ProofViews.Verification verify(@PathVariable UUID id,@RequestBody(required=false)String body){return service.verify(id,body);}
}
