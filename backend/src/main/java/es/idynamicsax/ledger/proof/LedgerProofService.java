package es.idynamicsax.ledger.proof;

import com.fasterxml.jackson.databind.JsonNode;
import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.idax.domain.AuditOperation;
import es.idynamicsax.idax.domain.AuditOriginSystem;
import es.idynamicsax.idax.service.audit.IdaxAuditService;
import es.idynamicsax.idax.service.audit.dto.AuditEventRequest;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.*;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerProofService {
    private static final Pattern SHA256=Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern PROFILE=Pattern.compile("^(RAW-BYTES-SHA256-V1|EXTERNAL:[A-Za-z0-9._:/-]{1,91})$");
    private final LedgerProofRepository proofs; private final LedgerSubmissionRepository submissions;
    private final LedgerProviderRegistry providers; private final LedgerProperties properties; private final ProofCanonicalizer canonicalizer; private final LedgerProofPersistence persistence; private final IdaxAuditService audit; private final AnchoringAccountLock accountLock;
    public LedgerProofService(LedgerProofRepository proofs,LedgerSubmissionRepository submissions,LedgerProviderRegistry providers,LedgerProperties properties,ProofCanonicalizer canonicalizer,LedgerProofPersistence persistence,IdaxAuditService audit,AnchoringAccountLock accountLock){this.proofs=proofs;this.submissions=submissions;this.providers=providers;this.properties=properties;this.canonicalizer=canonicalizer;this.persistence=persistence;this.audit=audit;this.accountLock=accountLock;}

    public ProofViews.Proof create(String idempotencyKey,String body){
        if(idempotencyKey==null||idempotencyKey.isBlank()||idempotencyKey.length()>255)throw new ProofValidationException("INVALID_IDEMPOTENCY_KEY","Idempotency-Key is required and limited to 255 characters");
        JsonNode root=canonicalizer.parseRequest(body); Normalized input=normalize(root); UUID tenant=tenantId();
        var existing=proofs.findByTenantIdAndIdempotencyKey(tenant,idempotencyKey);
        if(existing.isPresent()){
            LedgerProof proof=existing.get();
            if(!same(proof,input))throw new IdempotencyConflictException();
            return view(proof,submissions.findByProofIdAndNetworkId(proof.getId(),properties.anchoring().network()).orElse(null));
        }
        UUID user=userId(); LedgerProof newProof=new LedgerProof(tenant,input.externalId,input.proofType,input.hash,input.profile,input.metadata,idempotencyKey,user);
        var created=persistence.create(newProof,new LedgerSubmission(tenant,newProof.getId(),properties.anchoring().network(),"XRPL",user));
        LedgerProof proof=created.proof(); LedgerSubmission submission=created.submission();
        audit(proof,"LEDGER_PROOF_CREATED",AuditOperation.CREATE,Map.of("proofType",proof.getProofType(),"hashAlgorithm",proof.getHashAlgorithm(),"canonicalizationProfile",proof.getCanonicalizationProfile()));
        audit(proof,"LEDGER_SUBMISSION_CREATED",AuditOperation.CREATE,Map.of("networkId",submission.getNetworkId(),"providerType",submission.getProviderType()));
        var lockScope="XRPL|"+properties.anchoring().network()+"|"+properties.anchoring().account();
        try{
            try(var ignored=accountLock.acquire(lockScope,properties.recovery().lockTimeout())){
                var binding=binding(); LedgerProvider provider=providers.require(binding.network().providerType());
                PreparedAnchor prepared=provider.prepareAnchor(binding.network(),proof.getPublicId(),proof.getContentHash());
                created=persistence.signed(proof.getId(),submission.getId(),prepared); proof=created.proof(); submission=created.submission();
                audit(proof,"LEDGER_SUBMISSION_SIGNED",AuditOperation.SEND,Map.of("networkId",submission.getNetworkId(),"transactionHash",prepared.transactionHash()));
                provider.submitSignedAnchor(binding.network(),prepared);
                created=persistence.submitted(proof.getId(),submission.getId()); proof=created.proof(); submission=created.submission();
                audit(proof,"LEDGER_SUBMISSION_SUBMITTED",AuditOperation.SEND,Map.of("networkId",submission.getNetworkId(),"transactionHash",submission.getTransactionHash()));
                SubmittedAnchor validated=provider.awaitAnchorValidation(binding.network(),prepared);
                created=persistence.validated(proof.getId(),submission.getId(),validated); proof=created.proof(); submission=created.submission();
                audit(proof,"LEDGER_SUBMISSION_VALIDATED",AuditOperation.VALIDATE,Map.of("networkId",submission.getNetworkId(),"transactionHash",submission.getTransactionHash(),"ledgerIndex",submission.getLedgerIndex(),"ledgerHash",submission.getLedgerHash()));
            }
        }catch(Exception e){ProofStatus failed=classify(e);created=persistence.failed(proof.getId(),submission.getId(),failed,errorCode(e),safeDetail(e));proof=created.proof();submission=created.submission();audit(proof,"LEDGER_SUBMISSION_FAILED",AuditOperation.ERROR,Map.of("networkId",submission.getNetworkId(),"errorCode",errorCode(e)));throw e;}
        return view(proof,submission);
    }

    @Transactional(readOnly=true)
    public ProofViews.Proof get(UUID id){LedgerProof proof=proofs.findByIdAndTenantId(id,tenantId()).orElseThrow(ProofNotFoundException::new);return view(proof,submissions.findByProofIdAndNetworkId(id,properties.anchoring().network()).orElse(null));}
    public ProofViews.Proof getByPublicId(UUID publicId){LedgerProof proof=proofs.findByPublicIdAndTenantId(publicId,tenantId()).orElseThrow(ProofNotFoundException::new);return view(proof,submissions.findByProofIdAndNetworkId(proof.getId(),properties.anchoring().network()).orElse(null));}

    @Transactional(readOnly=true)
    public java.util.List<ProofViews.Proof> list(){return proofs.findAllByTenantIdOrderByCreatedAtDesc(tenantId()).stream().map(p->view(p,submissions.findByProofIdAndNetworkId(p.getId(),properties.anchoring().network()).orElse(null))).toList();}

    @Transactional(readOnly=true)
    public ProofViews.Summary summary(){var rows=proofs.findAllByTenantIdOrderByCreatedAtDesc(tenantId());long validated=rows.stream().filter(p->p.getStatus()==ProofStatus.VALIDATED).count();long failed=rows.stream().filter(p->p.getStatus()==ProofStatus.FAILED_RETRYABLE||p.getStatus()==ProofStatus.FAILED_PERMANENT).count();return new ProofViews.Summary(rows.size(),validated,rows.size()-validated-failed,failed);}

    @Transactional(readOnly=true)
    public ProofViews.Verification verify(UUID id,String optionalBody){
        LedgerProof proof=proofs.findByIdAndTenantId(id,tenantId()).orElseThrow(ProofNotFoundException::new);
        LedgerSubmission submission=submissions.findByProofIdAndNetworkId(id,properties.anchoring().network()).orElseThrow(ProofNotFoundException::new);
        ProofViews.Verification.Integrity integrity=integrity(proof,optionalBody);
        var binding=binding(); AnchorVerification verified=providers.require(binding.network().providerType()).verifyAnchor(binding.network(),proof.getPublicId(),proof.getContentHash(),submission.getTransactionHash(),submission.getLedgerHash(),submission.getAnchoringAccount());
        audit(proof,"LEDGER_PROOF_VERIFIED",AuditOperation.VALIDATE,Map.of("networkId",submission.getNetworkId(),"integrityStatus",integrity.status(),"ledgerStatus",verified.status().name()));
        return new ProofViews.Verification(integrity,new ProofViews.Verification.Ledger(verified.status().name(),verified.detail(),submission.getTransactionHash(),verified.ledgerIndex(),verified.ledgerHash()));
    }

    private ProofViews.Verification.Integrity integrity(LedgerProof proof,String body){
        if(body==null||body.isBlank())return new ProofViews.Verification.Integrity("NOT_EVALUATED",null,proof.getCanonicalizationProfile());
        Normalized supplied=normalize(canonicalizer.parseRequest(body));
        return new ProofViews.Verification.Integrity(proof.getContentHash().equals(supplied.hash)?"MATCH":"MISMATCH",supplied.hash,supplied.profile);
    }
    private Normalized normalize(JsonNode root){
        String external=text(root,"externalId",255);String type=text(root,"proofType",100);JsonNode payload=root.get("payload");String hash=root.path("hash").asText(null);
        if((payload==null)==(hash==null))throw new ProofValidationException("INVALID_INPUT_MODE","Provide exactly one of payload or hash");
        String normalizedHash,profile;
        if(payload!=null){var value=canonicalizer.canonicalize(payload);normalizedHash=value.hash();profile=ProofCanonicalizer.JCS_PROFILE;}
        else{if(!"SHA-256".equals(root.path("hashAlgorithm").asText()))throw new ProofValidationException("INVALID_HASH_ALGORITHM","Only SHA-256 is supported");if(!SHA256.matcher(hash).matches())throw new ProofValidationException("INVALID_HASH","SHA-256 hash must contain 64 hexadecimal characters");normalizedHash=hash.toLowerCase(Locale.ROOT);profile=text(root,"canonicalizationProfile",100);if(!PROFILE.matcher(profile).matches())throw new ProofValidationException("INVALID_CANONICALIZATION_PROFILE","Use RAW-BYTES-SHA256-V1 or EXTERNAL:<profile>");}
        JsonNode metadata=root.path("metadata");return new Normalized(external,type,normalizedHash,profile,metadata.isMissingNode()?"{}":metadata.toString());
    }
    private boolean same(LedgerProof p,Normalized n){return p.getExternalId().equals(n.externalId)&&p.getProofType().equals(n.proofType)&&p.getContentHash().equals(n.hash)&&p.getCanonicalizationProfile().equals(n.profile);}
    private String text(JsonNode root,String name,int max){String value=root.path(name).asText(null);if(value==null||value.isBlank()||value.length()>max)throw new ProofValidationException("INVALID_"+name.toUpperCase(Locale.ROOT),name+" is required and limited to "+max+" characters");return value;}
    private Binding binding(){LedgerProperties.Network network=properties.networks().stream().filter(n->n.id().equals(properties.anchoring().network())&&n.enabled()).findFirst().orElseThrow(()->new LedgerProviderException("Anchoring network is not enabled"));return new Binding(network);}
    private UUID tenantId(){var c=TenantContext.get();if(c==null||c.getTenantId()==null)throw new ProofValidationException("TENANT_REQUIRED","Tenant context is required");return c.getTenantId();}
    private UUID userId(){var c=TenantContext.get();return c==null?null:c.getAppUserId();}
    private ProofStatus classify(Exception e){String m=String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);return m.contains("wrong network")||m.contains("signature")||m.contains("insufficient")||m.contains("rejected")?ProofStatus.FAILED_PERMANENT:ProofStatus.FAILED_RETRYABLE;}
    private String errorCode(Exception e){return classify(e).name();} private String safeDetail(Exception e){String m=String.valueOf(e.getMessage());return m.length()>1000?m.substring(0,1000):m;}
    private void audit(LedgerProof proof,String eventType,AuditOperation operation,Map<String,Object> payload){audit.success(AuditEventRequest.builder().tenantId(proof.getTenantId()).originSystem(AuditOriginSystem.IDAX).eventType(eventType).operation(operation).entityName("LedgerProof").entityId(proof.getId().toString()).businessKey(proof.getPublicId().toString()).externalKey(proof.getExternalId()).correlationId(audit.currentCorrelationId()).payload(payload).build());}
    private ProofViews.Proof view(LedgerProof p,LedgerSubmission s){ProofViews.Submission sv=s==null?null:new ProofViews.Submission(s.getId(),s.getNetworkId(),s.getProviderType(),s.getStatus(),s.getAttemptCount(),s.getTransactionHash(),s.getLedgerIndex(),s.getLedgerHash(),s.getSubmittedAt(),s.getValidatedAt(),s.getAnchoringAccount(),s.getSequenceNumber(),s.getLastLedgerSequence());return new ProofViews.Proof(p.getId(),p.getPublicId(),p.getExternalId(),p.getProofType(),p.getHashAlgorithm(),p.getContentHash(),p.getCanonicalizationProfile(),p.getFormatVersion(),p.getMetadataJson(),p.getStatus(),p.getCreatedAt(),p.getCreatedBy(),sv);}
    private record Normalized(String externalId,String proofType,String hash,String profile,String metadata){} private record Binding(LedgerProperties.Network network){}
}
