package es.idynamicsax.ledger.proof;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import es.idynamicsax.idax.service.audit.IdaxAuditService;
import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.LedgerProviderRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LedgerProofFailureOrderingTest {
    @AfterEach void clearTenant(){TenantContext.clear();}

    @Test void databaseFailureBeforeDurableCreationNeverCallsTheProvider(){
        var proofs=mock(LedgerProofRepository.class);var submissions=mock(LedgerSubmissionRepository.class);
        var providers=mock(LedgerProviderRegistry.class);var persistence=mock(LedgerProofPersistence.class);
        var audit=mock(IdaxAuditService.class);var locks=mock(AnchoringAccountLock.class);
        var properties=new LedgerProperties(Duration.ofSeconds(1),Duration.ofSeconds(30),new LedgerProperties.Anchoring("private-xrpl","rAccount","seed",20),null,List.of());
        when(persistence.create(any(),any())).thenThrow(new IllegalStateException("database unavailable"));
        TenantContext.set(new TenantContext(UUID.randomUUID(),"test",UUID.randomUUID(),"phase7-db-failure",TenantContext.DbRole.IDAX_APP));
        var subject=new LedgerProofService(proofs,submissions,providers,properties,new ProofCanonicalizer(),persistence,audit,locks);
        assertThrows(IllegalStateException.class,()->subject.create("phase7-db-failure","{\"externalId\":\"DB-DOWN\",\"proofType\":\"FAILURE_TEST\",\"payload\":{\"value\":1}}"));
        verifyNoInteractions(providers,locks,audit);
    }
}
