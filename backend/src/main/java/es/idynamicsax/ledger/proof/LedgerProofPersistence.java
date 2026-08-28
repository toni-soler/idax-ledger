package es.idynamicsax.ledger.proof;

import es.idynamicsax.ledger.provider.PreparedAnchor;
import es.idynamicsax.ledger.provider.SubmittedAnchor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable state transitions. Each method commits independently of XRPL I/O. */
@Service
public class LedgerProofPersistence {
    private final LedgerProofRepository proofs;
    private final LedgerSubmissionRepository submissions;

    public LedgerProofPersistence(LedgerProofRepository proofs, LedgerSubmissionRepository submissions) {
        this.proofs = proofs;
        this.submissions = submissions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created create(LedgerProof proof, LedgerSubmission submission) {
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created signed(UUID proofId, UUID submissionId, PreparedAnchor prepared) {
        LedgerProof proof = requiredProof(proofId);
        LedgerSubmission submission = requiredSubmission(submissionId);
        submission.signed(prepared.transactionHash(), prepared.signedBlob(), prepared.sequence(), prepared.lastLedgerSequence(), prepared.anchoringAccount());
        proof.status(ProofStatus.SIGNED);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created submitted(UUID proofId, UUID submissionId) {
        LedgerProof proof = requiredProof(proofId);
        LedgerSubmission submission = requiredSubmission(submissionId);
        submission.submitted();
        proof.status(ProofStatus.SUBMITTED);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created validated(UUID proofId, UUID submissionId, SubmittedAnchor anchor) {
        LedgerProof proof = requiredProof(proofId);
        LedgerSubmission submission = requiredSubmission(submissionId);
        submission.validated(anchor.ledgerIndex(), anchor.ledgerHash());
        proof.status(ProofStatus.VALIDATED);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created failed(UUID proofId, UUID submissionId, ProofStatus status, String code, String detail) {
        LedgerProof proof = requiredProof(proofId);
        LedgerSubmission submission = requiredSubmission(submissionId);
        submission.failed(status, code, detail);
        proof.status(status);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created retryLater(UUID proofId, UUID submissionId, String code, String detail, java.time.OffsetDateTime retryAt) {
        LedgerProof proof = requiredProof(proofId); LedgerSubmission submission = requiredSubmission(submissionId);
        submission.retryLater(code, detail, retryAt); proof.status(ProofStatus.FAILED_RETRYABLE);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Created expired(UUID proofId, UUID submissionId, String detail) {
        LedgerProof proof = requiredProof(proofId); LedgerSubmission submission = requiredSubmission(submissionId);
        submission.expired(detail); proof.status(ProofStatus.FAILED_RETRYABLE);
        return new Created(proofs.saveAndFlush(proof), submissions.saveAndFlush(submission));
    }

    private LedgerProof requiredProof(UUID id) { return proofs.findById(id).orElseThrow(ProofNotFoundException::new); }
    private LedgerSubmission requiredSubmission(UUID id) { return submissions.findById(id).orElseThrow(ProofNotFoundException::new); }
    public record Created(LedgerProof proof, LedgerSubmission submission) {}
}
