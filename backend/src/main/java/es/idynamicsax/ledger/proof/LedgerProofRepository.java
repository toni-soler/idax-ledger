package es.idynamicsax.ledger.proof;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface LedgerProofRepository extends JpaRepository<LedgerProof,UUID>{ Optional<LedgerProof> findByTenantIdAndIdempotencyKey(UUID tenantId,String key); Optional<LedgerProof> findByIdAndTenantId(UUID id,UUID tenantId); Optional<LedgerProof> findByPublicIdAndTenantId(UUID publicId,UUID tenantId); List<LedgerProof> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId); }
