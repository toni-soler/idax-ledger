package es.idynamicsax.ledger.proof;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerSubmissionReconcilerTest {
    private final LedgerSubmissionRepository repository=mock(LedgerSubmissionRepository.class);
    private final LedgerProofRepository proofs=mock(LedgerProofRepository.class);
    private final LedgerProvider provider=mock(LedgerProvider.class); private final LedgerProviderRegistry registry=mock(LedgerProviderRegistry.class);
    private final LedgerProofPersistence persistence=mock(LedgerProofPersistence.class); private final AnchoringAccountLock locks=mock(AnchoringAccountLock.class);
    private final AnchoringAccountLock.Lease lease=mock(AnchoringAccountLock.Lease.class); private LedgerSubmissionReconciler subject; private LedgerSubmission submission;
    private final UUID submissionId=UUID.randomUUID(),proofId=UUID.randomUUID();
    @BeforeEach void setup(){
        var network=new LedgerProperties.Network("private-xrpl","Private","XRPL","PRIVATE",true,2181844733L,List.of(new LedgerProperties.Node("n1",URI.create("http://localhost"))));
        var properties=new LedgerProperties(Duration.ofSeconds(1),Duration.ofSeconds(30),new LedgerProperties.Anchoring("private-xrpl","rAccount","seed",20),new LedgerProperties.Recovery(true,Duration.ofSeconds(10),Duration.ofSeconds(1),Duration.ofSeconds(1)),List.of(network));
        when(registry.require("XRPL")).thenReturn(provider);when(locks.acquire(anyString(),any())).thenReturn(lease);
        submission=mock(LedgerSubmission.class);when(submission.getId()).thenReturn(submissionId);when(submission.getProofId()).thenReturn(proofId);when(submission.getStatus()).thenReturn(ProofStatus.FAILED_RETRYABLE);when(submission.getNetworkId()).thenReturn("private-xrpl");when(submission.getProviderType()).thenReturn("XRPL");when(submission.getAnchoringAccount()).thenReturn("rAccount");when(submission.getTransactionHash()).thenReturn("A".repeat(64));when(submission.getSignedTransactionBlob()).thenReturn("DEADBEEF");when(submission.getSequenceNumber()).thenReturn(7L);when(submission.getLastLedgerSequence()).thenReturn(100L);when(repository.findById(submissionId)).thenReturn(java.util.Optional.of(submission));
        subject=new LedgerSubmissionReconciler(repository,proofs,registry,properties,persistence,locks);
    }
    @Test void ambiguousSubmitAlreadyAcceptedReconcilesWithoutResubmit(){
        when(submission.getStatus()).thenReturn(ProofStatus.SIGNED);
        when(provider.inspectAnchor(any(),any())).thenReturn(new AnchorTransactionStatus(AnchorTransactionStatus.State.VALIDATED_SUCCESS,"tesSUCCESS",99L,"B".repeat(64)));
        subject.reconcile(submissionId);
        verify(provider,never()).submitSignedAnchor(any(),any());verify(persistence).validated(eq(proofId),eq(submissionId),any());
    }
    @Test void missingUnexpiredTransactionResubmitsExactPersistedBlob(){
        when(submission.getStatus()).thenReturn(ProofStatus.SIGNED);
        when(provider.inspectAnchor(any(),any())).thenReturn(new AnchorTransactionStatus(AnchorTransactionStatus.State.NOT_FOUND,null,null,null));when(provider.currentValidatedLedger(any())).thenReturn(90L);when(provider.awaitAnchorValidation(any(),any())).thenReturn(new SubmittedAnchor("A".repeat(64),101L,"B".repeat(64),"tesSUCCESS"));
        subject.reconcile(submissionId);
        verify(provider).submitSignedAnchor(any(),argThat(p->p.transactionHash().equals("A".repeat(64))&&p.signedBlob().equals("DEADBEEF")&&p.sequence()==7));verify(persistence).submitted(proofId,submissionId);
    }
    @Test void crashAfterSubmittedReconcilesThePersistedTransaction(){
        when(submission.getStatus()).thenReturn(ProofStatus.SUBMITTED);
        when(provider.inspectAnchor(any(),any())).thenReturn(new AnchorTransactionStatus(AnchorTransactionStatus.State.VALIDATED_SUCCESS,"tesSUCCESS",99L,"B".repeat(64)));
        subject.reconcile(submissionId);
        verify(provider,never()).submitSignedAnchor(any(),any());verify(persistence).validated(eq(proofId),eq(submissionId),any());
    }
    @Test void expiredAttemptNeverResignsOrResubmits(){
        when(provider.inspectAnchor(any(),any())).thenReturn(new AnchorTransactionStatus(AnchorTransactionStatus.State.NOT_FOUND,null,null,null));when(provider.currentValidatedLedger(any())).thenReturn(101L);
        subject.reconcile(submissionId);
        verify(provider,never()).submitSignedAnchor(any(),any());verify(persistence).expired(eq(proofId),eq(submissionId),contains("automatic re-signing is forbidden"));
    }
    @Test void neverSignedAttemptMayCreateItsFirstDurableTransaction(){
        LedgerSubmission pending=mock(LedgerSubmission.class); LedgerProof proof=mock(LedgerProof.class);
        when(pending.getId()).thenReturn(submissionId);when(pending.getProofId()).thenReturn(proofId);when(pending.getStatus()).thenReturn(ProofStatus.PENDING);when(pending.getNetworkId()).thenReturn("private-xrpl");when(pending.getProviderType()).thenReturn("XRPL");when(pending.getAnchoringAccount()).thenReturn("rAccount");
        PreparedAnchor prepared=new PreparedAnchor("C".repeat(64),"CAFE",8,110,2181844733L,"rAccount");
        when(repository.findById(submissionId)).thenReturn(java.util.Optional.of(pending),java.util.Optional.of(pending),java.util.Optional.of(submission));when(proofs.findById(proofId)).thenReturn(java.util.Optional.of(proof));when(proof.getPublicId()).thenReturn(UUID.randomUUID());when(proof.getContentHash()).thenReturn("D".repeat(64));when(provider.prepareAnchor(any(),any(),anyString())).thenReturn(prepared);when(provider.inspectAnchor(any(),any())).thenReturn(new AnchorTransactionStatus(AnchorTransactionStatus.State.VALIDATED_SUCCESS,"tesSUCCESS",109L,"E".repeat(64)));
        subject.reconcile(submissionId);
        var order=inOrder(provider,persistence);order.verify(provider).prepareAnchor(any(),any(),anyString());order.verify(persistence).signed(proofId,submissionId,prepared);verify(persistence).validated(eq(proofId),eq(submissionId),any());
    }
    @Test void advisoryKeyIsStableAndScopeSpecific(){org.junit.jupiter.api.Assertions.assertEquals(AnchoringAccountLock.key("XRPL|n|a"),AnchoringAccountLock.key("XRPL|n|a"));org.junit.jupiter.api.Assertions.assertNotEquals(AnchoringAccountLock.key("XRPL|n|a"),AnchoringAccountLock.key("XRPL|n|b"));}
}
