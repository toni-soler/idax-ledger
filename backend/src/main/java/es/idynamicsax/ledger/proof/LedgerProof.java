package es.idynamicsax.ledger.proof;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="ledger_proof", schema="idax_ledger")
public class LedgerProof {
    @Id private UUID id;
    @Column(name="public_id",nullable=false,unique=true) private UUID publicId;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="external_id",nullable=false,length=255) private String externalId;
    @Column(name="proof_type",nullable=false,length=100) private String proofType;
    @Column(name="hash_algorithm",nullable=false,length=20) private String hashAlgorithm;
    @Column(name="content_hash",nullable=false,length=64) private String contentHash;
    @Column(name="canonicalization_profile",nullable=false,length=100) private String canonicalizationProfile;
    @Column(name="format_version",nullable=false) private short formatVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="metadata_json",nullable=false,columnDefinition="jsonb") private String metadataJson;
    @Column(name="idempotency_key",nullable=false,length=255) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private ProofStatus status;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="updated_by") private UUID updatedBy;
    @Version private long version;

    protected LedgerProof() {}
    public LedgerProof(UUID tenantId,String externalId,String proofType,String contentHash,String profile,String metadataJson,String idempotencyKey,UUID userId){
        this.id=UUID.randomUUID(); this.publicId=UUID.randomUUID(); this.tenantId=tenantId; this.externalId=externalId;
        this.proofType=proofType; this.hashAlgorithm="SHA-256"; this.contentHash=contentHash; this.canonicalizationProfile=profile;
        this.formatVersion=1; this.metadataJson=metadataJson; this.idempotencyKey=idempotencyKey; this.status=ProofStatus.PENDING;
        this.createdAt=OffsetDateTime.now(); this.updatedAt=createdAt; this.createdBy=userId; this.updatedBy=userId;
    }
    public void status(ProofStatus value){status=value;updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public UUID getPublicId(){return publicId;} public UUID getTenantId(){return tenantId;}
    public String getExternalId(){return externalId;} public String getProofType(){return proofType;} public String getHashAlgorithm(){return hashAlgorithm;}
    public String getContentHash(){return contentHash;} public String getCanonicalizationProfile(){return canonicalizationProfile;}
    public short getFormatVersion(){return formatVersion;} public String getMetadataJson(){return metadataJson;}
    public String getIdempotencyKey(){return idempotencyKey;} public ProofStatus getStatus(){return status;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public UUID getCreatedBy(){return createdBy;} public long getVersion(){return version;}
}
