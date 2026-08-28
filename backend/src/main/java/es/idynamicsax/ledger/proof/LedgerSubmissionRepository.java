package es.idynamicsax.ledger.proof;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
public interface LedgerSubmissionRepository extends JpaRepository<LedgerSubmission,UUID>{
 Optional<LedgerSubmission> findByProofIdAndNetworkId(UUID proofId,String networkId);
 @Query("select s from LedgerSubmission s where s.status in :statuses and (s.nextRetryAt is null or s.nextRetryAt <= :now) order by s.updatedAt")
 List<LedgerSubmission> findRecoverable(@Param("statuses") Collection<ProofStatus> statuses,@Param("now") java.time.OffsetDateTime now,org.springframework.data.domain.Pageable page);
}
