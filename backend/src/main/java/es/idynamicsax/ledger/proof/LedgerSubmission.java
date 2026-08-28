package es.idynamicsax.ledger.proof;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="ledger_submission",schema="idax_ledger")
public class LedgerSubmission {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="proof_id",nullable=false) private UUID proofId;
    @Column(name="network_id",nullable=false,length=100) private String networkId;
    @Column(name="provider_type",nullable=false,length=32) private String providerType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private ProofStatus status;
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="next_retry_at") private OffsetDateTime nextRetryAt;
    @Column(name="last_error_code") private String lastErrorCode;
    @Column(name="last_error_detail") private String lastErrorDetail;
    @Column(name="transaction_hash",length=64) private String transactionHash;
    @Column(name="signed_transaction_blob",columnDefinition="text") private String signedTransactionBlob;
    @Column(name="sequence_number") private Long sequenceNumber;
    @Column(name="last_ledger_sequence") private Long lastLedgerSequence;
    @Column(name="anchoring_account",length=64) private String anchoringAccount;
    @Column(name="reconciled_at") private OffsetDateTime reconciledAt;
    @Column(name="ledger_index") private Long ledgerIndex;
    @Column(name="ledger_hash",length=64) private String ledgerHash;
    @Column(name="submitted_at") private OffsetDateTime submittedAt;
    @Column(name="validated_at") private OffsetDateTime validatedAt;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="updated_by") private UUID updatedBy;
    @Version private long version;
    protected LedgerSubmission(){}
    public LedgerSubmission(UUID tenantId,UUID proofId,String networkId,String providerType,UUID userId){this.id=UUID.randomUUID();this.tenantId=tenantId;this.proofId=proofId;this.networkId=networkId;this.providerType=providerType;this.status=ProofStatus.PENDING;this.createdAt=OffsetDateTime.now();this.updatedAt=createdAt;this.createdBy=userId;this.updatedBy=userId;}
    public void signed(String hash,String blob,long sequence,long lastLedger,String account){transactionHash=hash;signedTransactionBlob=blob;sequenceNumber=sequence;lastLedgerSequence=lastLedger;anchoringAccount=account;status=ProofStatus.SIGNED;updatedAt=OffsetDateTime.now();}
    public void submitted(){attemptCount++;submittedAt=OffsetDateTime.now();status=ProofStatus.SUBMITTED;updatedAt=submittedAt;}
    public void validated(long index,String hash){ledgerIndex=index;ledgerHash=hash;validatedAt=OffsetDateTime.now();status=ProofStatus.VALIDATED;updatedAt=validatedAt;}
    public void reconciled(){reconciledAt=OffsetDateTime.now();updatedAt=reconciledAt;}
    public void retryLater(String code,String detail,OffsetDateTime retryAt){status=ProofStatus.FAILED_RETRYABLE;lastErrorCode=code;lastErrorDetail=detail;nextRetryAt=retryAt;reconciled();}
    public void expired(String detail){status=ProofStatus.ATTEMPT_EXPIRED;lastErrorCode="LAST_LEDGER_SEQUENCE_EXPIRED";lastErrorDetail=detail;nextRetryAt=null;reconciled();}
    public void failed(ProofStatus value,String code,String detail){status=value;lastErrorCode=code;lastErrorDetail=detail;updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getProofId(){return proofId;}
    public String getNetworkId(){return networkId;} public String getProviderType(){return providerType;} public ProofStatus getStatus(){return status;}
    public int getAttemptCount(){return attemptCount;} public String getTransactionHash(){return transactionHash;} public String getSignedTransactionBlob(){return signedTransactionBlob;}
    public Long getSequenceNumber(){return sequenceNumber;} public Long getLastLedgerSequence(){return lastLedgerSequence;} public String getAnchoringAccount(){return anchoringAccount;} public OffsetDateTime getNextRetryAt(){return nextRetryAt;} public OffsetDateTime getReconciledAt(){return reconciledAt;}
    public Long getLedgerIndex(){return ledgerIndex;} public String getLedgerHash(){return ledgerHash;} public OffsetDateTime getSubmittedAt(){return submittedAt;} public OffsetDateTime getValidatedAt(){return validatedAt;} public OffsetDateTime getCreatedAt(){return createdAt;}
}
