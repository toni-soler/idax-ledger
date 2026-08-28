package es.idynamicsax.ledger.proof;

import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.*;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reconciles durable attempts. It signs only an initial attempt that never reached SIGNED. */
@Service
public class LedgerSubmissionReconciler {
    private static final EnumSet<ProofStatus> RECOVERABLE=EnumSet.of(ProofStatus.PENDING,ProofStatus.SIGNED,ProofStatus.SUBMITTED,ProofStatus.FAILED_RETRYABLE);
    private final LedgerSubmissionRepository submissions; private final LedgerProofRepository proofs; private final LedgerProviderRegistry providers; private final LedgerProperties properties; private final LedgerProofPersistence persistence; private final AnchoringAccountLock locks;
    public LedgerSubmissionReconciler(LedgerSubmissionRepository submissions,LedgerProofRepository proofs,LedgerProviderRegistry providers,LedgerProperties properties,LedgerProofPersistence persistence,AnchoringAccountLock locks){this.submissions=submissions;this.proofs=proofs;this.providers=providers;this.properties=properties;this.persistence=persistence;this.locks=locks;}

    @EventListener(ApplicationReadyEvent.class) public void startup(){if(properties.recovery().enabled())reconcileBatch();}
    @Scheduled(fixedDelayString="${idax.ledger.recovery.interval:10s}",initialDelayString="${idax.ledger.recovery.interval:10s}")
    public void scheduled(){if(properties.recovery().enabled())reconcileBatch();}

    public int reconcileBatch(){int count=0;for(var submission:submissions.findRecoverable(RECOVERABLE,OffsetDateTime.now(),PageRequest.of(0,100))){try{reconcile(submission.getId());count++;}catch(Exception ignored){/* durable retry state is written by reconcile */}}return count;}

    public void reconcile(java.util.UUID id){
        LedgerSubmission candidate=submissions.findById(id).orElseThrow(ProofNotFoundException::new);
        if(!RECOVERABLE.contains(candidate.getStatus()))return;
        var network=properties.networks().stream().filter(n->n.id().equals(candidate.getNetworkId())&&n.enabled()).findFirst().orElseThrow(()->new LedgerProviderException("Recovery network is not enabled"));
        String account=candidate.getAnchoringAccount()==null?properties.anchoring().account():candidate.getAnchoringAccount();
        String scope=candidate.getProviderType()+"|"+candidate.getNetworkId()+"|"+account;
        try(var ignored=locks.acquire(scope,properties.recovery().lockTimeout())){
            LedgerSubmission current=submissions.findById(id).orElseThrow(ProofNotFoundException::new);
            if(!RECOVERABLE.contains(current.getStatus()))return;
            LedgerProvider provider=providers.require(current.getProviderType());
            boolean neverSigned=current.getTransactionHash()==null&&current.getSignedTransactionBlob()==null;
            if(neverSigned){
                LedgerProof proof=proofs.findById(current.getProofId()).orElseThrow(ProofNotFoundException::new);
                PreparedAnchor initial=provider.prepareAnchor(network,proof.getPublicId(),proof.getContentHash());
                persistence.signed(current.getProofId(),id,initial);
                current=submissions.findById(id).orElseThrow(ProofNotFoundException::new);
            }
            if(current.getTransactionHash()==null||current.getSignedTransactionBlob()==null||current.getLastLedgerSequence()==null){persistence.failed(current.getProofId(),id,ProofStatus.FAILED_PERMANENT,"INCOMPLETE_SIGNED_ATTEMPT","A partially persisted signed attempt cannot be reconstructed safely");return;}
            PreparedAnchor prepared=new PreparedAnchor(current.getTransactionHash(),current.getSignedTransactionBlob(),current.getSequenceNumber()==null?0:current.getSequenceNumber(),current.getLastLedgerSequence(),network.networkId(),account);
            AnchorTransactionStatus actual=provider.inspectAnchor(network,prepared);
            if(actual.state()==AnchorTransactionStatus.State.VALIDATED_SUCCESS){persistence.validated(current.getProofId(),id,new SubmittedAnchor(current.getTransactionHash(),actual.ledgerIndex(),actual.ledgerHash(),actual.resultCode()));return;}
            if(actual.state()==AnchorTransactionStatus.State.VALIDATED_FAILURE){persistence.failed(current.getProofId(),id,ProofStatus.FAILED_PERMANENT,"XRPL_"+actual.resultCode(),"Persisted transaction validated with a non-success result");return;}
            if(actual.state()==AnchorTransactionStatus.State.FOUND_NOT_VALIDATED){persistence.retryLater(current.getProofId(),id,"AWAITING_VALIDATION","Transaction exists but is not validated",OffsetDateTime.now().plus(properties.recovery().retryDelay()));return;}
            long ledger=provider.currentValidatedLedger(network);
            if(ledger>prepared.lastLedgerSequence()){persistence.expired(current.getProofId(),id,"Signed attempt expired at ledger "+prepared.lastLedgerSequence()+"; automatic re-signing is forbidden");return;}
            provider.submitSignedAnchor(network,prepared); persistence.submitted(current.getProofId(),id);
            SubmittedAnchor validated=provider.awaitAnchorValidation(network,prepared); persistence.validated(current.getProofId(),id,validated);
        }catch(Exception e){
            LedgerSubmission latest=submissions.findById(id).orElse(null);
            if(latest!=null&&RECOVERABLE.contains(latest.getStatus()))persistence.retryLater(latest.getProofId(),id,"RECONCILIATION_RETRY",safe(e),OffsetDateTime.now().plus(properties.recovery().retryDelay()));
            throw e;
        }
    }
    private String safe(Exception e){String value=String.valueOf(e.getMessage());return value.length()>1000?value.substring(0,1000):value;}
}
