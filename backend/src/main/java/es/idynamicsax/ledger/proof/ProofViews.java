package es.idynamicsax.ledger.proof;
import java.time.OffsetDateTime; import java.util.UUID;
public final class ProofViews {private ProofViews(){}
 public record Submission(UUID id,String networkId,String providerType,ProofStatus status,int attemptCount,String transactionHash,Long ledgerIndex,String ledgerHash,OffsetDateTime submittedAt,OffsetDateTime validatedAt,String anchoringAccount,Long sequenceNumber,Long lastLedgerSequence){}
 public record Proof(UUID id,UUID publicId,String externalId,String proofType,String hashAlgorithm,String contentHash,String canonicalizationProfile,short formatVersion,String metadataJson,ProofStatus status,OffsetDateTime createdAt,UUID createdBy,Submission submission){}
 public record Summary(long total,long validated,long pending,long failed){}
 public record Verification(Integrity integrity,Ledger ledger){public record Integrity(String status,String calculatedHash,String profile){} public record Ledger(String status,String detail,String transactionHash,Long ledgerIndex,String ledgerHash){}}
}
